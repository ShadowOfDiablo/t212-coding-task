package com.tihomir.t212.tradingbot.bot.live;

import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.bot.model.LiveBotStatus;
import com.tihomir.t212.tradingbot.bot.model.PortfolioPoint;
import com.tihomir.t212.tradingbot.bot.run.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.run.RunCounts;
import com.tihomir.t212.tradingbot.bot.run.RunEnd;
import com.tihomir.t212.tradingbot.bot.run.RunStatus;
import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleUpdateListener;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuote;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.position.PositionWriteList;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One running live bot. Prices arrive on every candle update ({@code liveOnTick}) or on a timer,
 * and are evaluated one at a time on the bot's own worker thread, so the feeder never waits.
 */
final class LiveTradingBot implements CandleUpdateListener {

  static final int EVALUATION_QUEUE_CAPACITY = 100;
  static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);
  static final Duration PORTFOLIO_POINT_INTERVAL = Duration.ofSeconds(15);
  static final int MAX_PORTFOLIO_POINTS = 1000;

  private static final Logger log = LoggerFactory.getLogger(LiveTradingBot.class);
  private static final int SINGLE_THREAD = 1;
  private static final long NO_KEEP_ALIVE = 0;

  private final Broker broker;
  private volatile long runId;
  private final LiveTradingAccount account;
  private final OrderCheckerService orderChecker;
  private final Clock clock;
  private final ThreadFactory threadFactory;
  private final ThreadPoolExecutor worker;
  private volatile LiveSignalSource signalSource;
  private long algorithmId;
  private boolean paused;
  private Optional<ScheduledExecutorService> timer = Optional.empty();
  private int buyCount;
  private int sellCount;
  private int stopLossCount;
  private int takeProfitCount;
  private int refusedOrderCount;
  private final Deque<PortfolioPoint> points = new ArrayDeque<>();
  private Instant lastPointTime = Instant.EPOCH;

  LiveTradingBot(
      Broker broker,
      long runId,
      LiveTradingAccount account,
      OrderCheckerService orderChecker,
      LiveSignalSource signalSource,
      long algorithmId,
      Clock clock) {
    this.broker = broker;
    this.runId = runId;
    this.account = account;
    this.orderChecker = orderChecker;
    this.signalSource = signalSource;
    this.algorithmId = algorithmId;
    this.clock = clock;
    this.threadFactory =
        Thread.ofPlatform().daemon().name(broker.name() + "-live-bot-", 0).factory();
    this.worker =
        new ThreadPoolExecutor(
            SINGLE_THREAD,
            SINGLE_THREAD,
            NO_KEEP_ALIVE,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(EVALUATION_QUEUE_CAPACITY),
            threadFactory);
  }

  long runId() {
    return runId;
  }

  String brokerName() {
    return broker.name();
  }

  BigDecimal cash() {
    return account.cash();
  }

  String ticker() {
    return signalSource.ticker();
  }

  synchronized void startListening() {
    broker.candleFeeder().addListener(this);
    scheduleTimedEvaluations(signalSource.settings());
  }

  @Override
  public void onCandleUpdate(CandleUpdate update) {
    if (!update.ticker().equals(ticker()) || !signalSource.settings().liveOnTick()) {
      return;
    }
    submitEvaluation(update.close(), update.eventTime());
  }

  /**
   * Runs on the worker thread. A failure is logged and the bot goes on with the next price. While
   * paused, only MAE, MFE and the portfolio chart follow the price.
   */
  synchronized void evaluate(BigDecimal price, Instant time) {
    try {
      if (paused) {
        account.updateExcursions(price);
      } else {
        evaluatePrice(price, time);
      }
      recordPortfolioPoint(price, time);
    } catch (RuntimeException e) {
      log.error(
          "{} {}: live run {} could not evaluate the price {}",
          broker.name(),
          ticker(),
          runId,
          price,
          e);
    }
  }

  /**
   * Goes on as a new run with another algorithm variant, from the next evaluation. Counts and
   * portfolio points start again.
   */
  synchronized void startNextRun(
      long nextRunId, LiveSignalSource newSignalSource, long newAlgorithmId) {
    runId = nextRunId;
    account.startRun(nextRunId);
    signalSource = newSignalSource;
    algorithmId = newAlgorithmId;
    buyCount = 0;
    sellCount = 0;
    stopLossCount = 0;
    takeProfitCount = 0;
    refusedOrderCount = 0;
    points.clear();
    lastPointTime = Instant.EPOCH;
    timer.ifPresent(ScheduledExecutorService::shutdownNow);
    timer = Optional.empty();
    scheduleTimedEvaluations(newSignalSource.settings());
  }

  /** While paused no order is placed, not even a stop loss or take profit. */
  synchronized void setPaused(boolean paused) {
    this.paused = paused;
  }

  synchronized LiveBotStatus status() {
    return new LiveBotStatus(
        runId, ticker(), paused, signalSource.settings(), algorithmId, counts(), portfolioPoints());
  }

  /** Stops listening, lets the queued prices finish and tells how the run ended. */
  RunEnd stop() {
    broker.candleFeeder().removeListener(this);
    stopTimer();
    stopWorker();
    return runEnd(RunStatus.STOPPED);
  }

  private void evaluatePrice(BigDecimal price, Instant time) {
    account.updateExcursions(price);
    AlgorithmSettings settings = signalSource.settings();
    if (orderChecker.isStopLossReached(account, price, settings)) {
      sellAtStopLoss(price, settings);
      return;
    }
    if (orderChecker.isTakeProfitReached(account, price, settings)) {
      sellAtTakeProfit(price, settings);
      return;
    }
    TradingSignal signal = signalSource.signalAt(price, time);
    if (signal == TradingSignal.HOLD) {
      return;
    }
    if (!placeCheckedOrder(signal, price, settings)) {
      refusedOrderCount++;
      return;
    }
    if (signal == TradingSignal.BUY) {
      buyCount++;
      return;
    }
    sellCount++;
  }

  private void sellAtStopLoss(BigDecimal price, AlgorithmSettings settings) {
    if (!placeCheckedOrder(TradingSignal.SELL, price, settings)) {
      refusedOrderCount++;
      return;
    }
    sellCount++;
    stopLossCount++;
  }

  private void sellAtTakeProfit(BigDecimal price, AlgorithmSettings settings) {
    if (!placeCheckedOrder(TradingSignal.SELL, price, settings)) {
      refusedOrderCount++;
      return;
    }
    sellCount++;
    takeProfitCount++;
  }

  private boolean placeCheckedOrder(
      TradingSignal signal, BigDecimal price, AlgorithmSettings settings) {
    Optional<ExecutedOrder> order =
        orderChecker.placeCheckedOrder(broker.name(), signal, account, price, settings);
    order.ifPresent(account::recordExecutedOrder);
    return order.isPresent();
  }

  private void submitEvaluation(BigDecimal price, Instant time) {
    try {
      worker.execute(() -> evaluate(price, time));
    } catch (RejectedExecutionException e) {
      log.warn(
          "{} {}: live run {} dropped the price {}, the bot is busy or stopping",
          broker.name(),
          ticker(),
          runId,
          price);
    }
  }

  private void scheduleTimedEvaluations(AlgorithmSettings settings) {
    if (settings.liveOnTick()) {
      return;
    }
    long updateTimeSeconds = settings.updateTimeSeconds().orElseThrow();
    ScheduledExecutorService newTimer = Executors.newSingleThreadScheduledExecutor(threadFactory);
    newTimer.scheduleWithFixedDelay(
        this::submitEvaluationAtLatestPrice,
        updateTimeSeconds,
        updateTimeSeconds,
        TimeUnit.SECONDS);
    timer = Optional.of(newTimer);
  }

  /** Runs on the timer thread; it must not throw, or the timer stops. */
  private void submitEvaluationAtLatestPrice() {
    try {
      Optional<BigDecimal> lastPrice =
          broker.tickerPrices().get(ticker()).map(TickerQuote::lastPrice);
      if (lastPrice.isEmpty()) {
        log.debug("{} {}: no live price yet", broker.name(), ticker());
        return;
      }
      submitEvaluation(lastPrice.get(), clock.instant());
    } catch (RuntimeException e) {
      log.error("{} {}: live run {} timer failed", broker.name(), ticker(), runId, e);
    }
  }

  private synchronized void stopTimer() {
    timer.ifPresent(ScheduledExecutorService::shutdownNow);
    timer = Optional.empty();
  }

  private void stopWorker() {
    worker.shutdown();
    try {
      if (!worker.awaitTermination(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        log.warn(
            "{} {}: live run {} did not finish its queued prices in time, dropping them",
            broker.name(),
            ticker(),
            runId);
        worker.shutdownNow();
      }
    } catch (InterruptedException e) {
      worker.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  synchronized RunEnd runEnd(RunStatus status) {
    return new RunEnd(
        status,
        clock.instant(),
        finalValue(),
        counts(),
        Optional.empty(),
        portfolioPoints(),
        PositionWriteList.NONE);
  }

  /** Empty when there is no live price or the balances can't be read. */
  private Optional<BigDecimal> finalValue() {
    try {
      return broker
          .tickerPrices()
          .get(ticker())
          .map(TickerQuote::lastPrice)
          .map(account::portfolioValue);
    } catch (RuntimeException e) {
      log.error(
          "{} {}: live run {} could not read its final value", broker.name(), ticker(), runId, e);
      return Optional.empty();
    }
  }

  /** At most one point per interval; the oldest go once there are too many. */
  private void recordPortfolioPoint(BigDecimal price, Instant time) {
    if (Duration.between(lastPointTime, time).compareTo(PORTFOLIO_POINT_INTERVAL) < 0) {
      return;
    }
    BigDecimal cash = account.cash();
    points.addLast(new PortfolioPoint(time, cash, account.portfolioValue(price)));
    lastPointTime = time;
    if (points.size() > MAX_PORTFOLIO_POINTS) {
      points.removeFirst();
    }
  }

  private PortfolioPointList portfolioPoints() {
    return new PortfolioPointList(List.copyOf(points));
  }

  private RunCounts counts() {
    return new RunCounts(buyCount, sellCount, stopLossCount, takeProfitCount, refusedOrderCount);
  }
}
