package com.tihomir.t212.tradingbot.bot.live;

import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.bot.live.queue.LiveEvaluationQueue;
import com.tihomir.t212.tradingbot.bot.live.queue.PriceMessage;
import com.tihomir.t212.tradingbot.bot.live.queue.PriceMessageHandler;
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
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One running live bot. Prices arrive on every candle update ({@code liveOnTick}) or on a timer and
 * go to the broker's evaluation queue; its workers call back here, one price of this ticker at a
 * time. A price older than the last one evaluated is discarded.
 */
final class LiveTradingBot implements CandleUpdateListener, PriceMessageHandler {

  static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);
  static final Duration PORTFOLIO_POINT_INTERVAL = Duration.ofSeconds(15);
  static final int MAX_PORTFOLIO_POINTS = 1000;

  private static final Logger log = LoggerFactory.getLogger(LiveTradingBot.class);

  private final Broker broker;
  private volatile long runId;
  private final LiveTradingAccount account;
  private final OrderCheckerService orderChecker;
  private final LiveEvaluationQueue evaluationQueue;
  private final Clock clock;
  private final ThreadFactory timerThreadFactory;
  private volatile LiveSignalSource signalSource;
  private long algorithmId;
  private boolean paused;
  private Optional<ScheduledExecutorService> timer = Optional.empty();
  private int buyCount;
  private int sellCount;
  private int stopLossCount;
  private int takeProfitCount;
  private int refusedOrderCount;
  private final AtomicInteger pendingMessageCount = new AtomicInteger();
  private final Object pendingMessagesLock = new Object();
  private Instant lastEvaluatedTime = Instant.EPOCH;
  private final Deque<PortfolioPoint> points = new ArrayDeque<>();
  private Instant lastPointTime = Instant.EPOCH;

  LiveTradingBot(
      Broker broker,
      long runId,
      LiveTradingAccount account,
      OrderCheckerService orderChecker,
      LiveSignalSource signalSource,
      long algorithmId,
      LiveEvaluationQueue evaluationQueue,
      Clock clock) {
    this.broker = broker;
    this.runId = runId;
    this.account = account;
    this.orderChecker = orderChecker;
    this.signalSource = signalSource;
    this.algorithmId = algorithmId;
    this.evaluationQueue = evaluationQueue;
    this.clock = clock;
    this.timerThreadFactory =
        Thread.ofPlatform().daemon().name(broker.name() + "-live-timer-", 0).factory();
  }

  long runId() {
    return runId;
  }

  String brokerName() {
    return broker.name();
  }

  /** The bot's own cash, what its ticker's budget becomes when no price is known. */
  BigDecimal cash() {
    return account.ownCash();
  }

  String ticker() {
    return signalSource.ticker();
  }

  synchronized void startListening() {
    evaluationQueue.registerHandler(ticker(), this);
    broker.candleFeeder().addListener(this);
    scheduleTimedEvaluations(signalSource.settings());
  }

  @Override
  public void onCandleUpdate(CandleUpdate update) {
    if (!update.ticker().equals(ticker()) || !signalSource.settings().liveOnTick()) {
      return;
    }
    queuePrice(update.close(), update.eventTime());
  }

  /** Runs on a worker; a price older than the last one evaluated is discarded. */
  @Override
  public synchronized void evaluatePriceMessage(PriceMessage message) {
    if (message.time().isBefore(lastEvaluatedTime)) {
      log.debug("{} {}: discarded a price older than the last one", broker.name(), ticker());
      return;
    }
    evaluate(message.price(), message.time());
    lastEvaluatedTime = message.time();
  }

  /** An exchange rejection that is not retried any more counts as a refused order. */
  @Override
  public synchronized void recordFailedEvaluation(PriceMessage message, RuntimeException failure) {
    if (failure instanceof ExchangeRejectedRequestException) {
      refusedOrderCount++;
    }
    log.error(
        "{} {}: live run {} could not evaluate the price {} after {} tries",
        broker.name(),
        ticker(),
        runId,
        message.price(),
        message.attempt(),
        failure);
  }

  @Override
  public void finishPriceMessage() {
    synchronized (pendingMessagesLock) {
      pendingMessageCount.decrementAndGet();
      pendingMessagesLock.notifyAll();
    }
  }

  /**
   * Evaluates one price. A failure is thrown to the caller. While paused, only MAE, MFE and the
   * portfolio chart follow the price.
   */
  synchronized void evaluate(BigDecimal price, Instant time) {
    if (paused) {
      account.updateExcursions(price);
    } else {
      evaluatePrice(price, time);
    }
    recordPortfolioPoint(price, time);
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

  /** Stops listening, lets the queued prices finish (up to the timeout) and tells how it ended. */
  RunEnd stop() {
    broker.candleFeeder().removeListener(this);
    stopTimer();
    awaitPendingMessages();
    evaluationQueue.unregisterHandler(ticker());
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

  /**
   * @return false when the order checks refused it; an exchange rejection is thrown
   */
  private boolean placeCheckedOrder(
      TradingSignal signal, BigDecimal price, AlgorithmSettings settings) {
    Optional<ExecutedOrder> order =
        orderChecker.placeCheckedOrder(broker.name(), signal, account, price, settings);
    order.ifPresent(account::recordExecutedOrder);
    return order.isPresent();
  }

  /** Never blocks: it runs on the feeder's dispatcher and the timer thread. */
  private void queuePrice(BigDecimal price, Instant time) {
    pendingMessageCount.incrementAndGet();
    if (!evaluationQueue.offerLast(PriceMessage.firstAttempt(ticker(), price, time))) {
      pendingMessageCount.decrementAndGet();
    }
  }

  private void scheduleTimedEvaluations(AlgorithmSettings settings) {
    if (settings.liveOnTick()) {
      return;
    }
    long updateTimeSeconds = settings.updateTimeSeconds().orElseThrow();
    ScheduledExecutorService newTimer =
        Executors.newSingleThreadScheduledExecutor(timerThreadFactory);
    newTimer.scheduleWithFixedDelay(
        this::queueLatestPrice, updateTimeSeconds, updateTimeSeconds, TimeUnit.SECONDS);
    timer = Optional.of(newTimer);
  }

  /** Runs on the timer thread; it must not throw, or the timer stops. */
  private void queueLatestPrice() {
    try {
      Optional<BigDecimal> lastPrice =
          broker.tickerPrices().get(ticker()).map(TickerQuote::lastPrice);
      if (lastPrice.isEmpty()) {
        log.debug("{} {}: no live price yet", broker.name(), ticker());
        return;
      }
      queuePrice(lastPrice.get(), clock.instant());
    } catch (RuntimeException e) {
      log.error("{} {}: live run {} timer failed", broker.name(), ticker(), runId, e);
    }
  }

  private synchronized void stopTimer() {
    timer.ifPresent(ScheduledExecutorService::shutdownNow);
    timer = Optional.empty();
  }

  private void awaitPendingMessages() {
    long deadline = System.nanoTime() + STOP_TIMEOUT.toNanos();
    synchronized (pendingMessagesLock) {
      try {
        while (pendingMessageCount.get() > 0 && System.nanoTime() < deadline) {
          TimeUnit.NANOSECONDS.timedWait(pendingMessagesLock, deadline - System.nanoTime());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (pendingMessageCount.get() > 0) {
      log.warn(
          "{} {}: live run {} stopped with {} prices still queued, they are dropped",
          broker.name(),
          ticker(),
          runId,
          pendingMessageCount.get());
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
