package com.tihomir.t212.tradingbot.bot.live;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.exception.LiveBotNotRunningException;
import com.tihomir.t212.tradingbot.bot.exception.LiveBotRunningException;
import com.tihomir.t212.tradingbot.bot.exception.TickerSettingsMissingException;
import com.tihomir.t212.tradingbot.bot.exception.TradingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.live.queue.EvaluationWorkerPool;
import com.tihomir.t212.tradingbot.bot.live.queue.LiveEvaluationQueue;
import com.tihomir.t212.tradingbot.bot.model.LiveBotStatus;
import com.tihomir.t212.tradingbot.bot.model.LiveBotStatusList;
import com.tihomir.t212.tradingbot.bot.run.AlgorithmRunStore;
import com.tihomir.t212.tradingbot.bot.run.RunEnd;
import com.tihomir.t212.tradingbot.bot.run.RunStart;
import com.tihomir.t212.tradingbot.bot.run.RunStatus;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.tickersettings.AssignedSettings;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettings;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettingsService;
import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuote;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.position.PositionStore;
import com.tihomir.t212.tradingbot.position.PositionTracker;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Starts, changes and stops the live bots, one per broker and ticker, and records their runs. Each
 * bot trades with its ticker's assigned settings and budget on the exchange's demo account; every
 * order goes through {@link OrderCheckerService}.
 */
@Service
public class TradingBotService {

  private static final Logger log = LoggerFactory.getLogger(TradingBotService.class);
  private static final int MIN_EVALUATION_WORKERS = 2;

  private final BotModeService botModeService;
  private final AlgorithmRegistryService algorithms;
  private final AlgorithmSettingsService settingsService;
  private final TickerSettingsService tickerSettings;
  private final CandleHistoryService candleHistory;
  private final OrderCheckerService orderChecker;
  private final AlgorithmRunStore runStore;
  private final PositionStore positionStore;
  private final Clock clock;
  private final Map<RunningBotKey, LiveTradingBot> runningBots = new ConcurrentHashMap<>();
  private final Map<String, LiveEvaluationQueue> evaluationQueues = new ConcurrentHashMap<>();
  private final Map<String, EvaluationWorkerPool> workerPools = new ConcurrentHashMap<>();

  public TradingBotService(
      BotModeService botModeService,
      AlgorithmRegistryService algorithms,
      AlgorithmSettingsService settingsService,
      TickerSettingsService tickerSettings,
      CandleHistoryService candleHistory,
      OrderCheckerService orderChecker,
      AlgorithmRunStore runStore,
      PositionStore positionStore,
      Clock clock) {
    this.botModeService = botModeService;
    this.algorithms = algorithms;
    this.settingsService = settingsService;
    this.tickerSettings = tickerSettings;
    this.candleHistory = candleHistory;
    this.orderChecker = orderChecker;
    this.runStore = runStore;
    this.positionStore = positionStore;
    this.clock = clock;
  }

  /**
   * Starts the ticker's bot with its assigned settings and budget: warms the algorithm up from the
   * candle store, records the start and starts listening. Nothing is recorded when a check fails.
   *
   * @return the id of the recorded run
   * @throws TradingModeRequiredException unless the bot is in trading mode
   * @throws LiveBotRunningException if the ticker's bot already runs
   * @throws TickerSettingsMissingException if the ticker has no settings yet
   */
  public synchronized long startBot(String broker, String ticker) {
    if (botModeService.botMode() != BotMode.TRADING) {
      throw new TradingModeRequiredException();
    }
    RunningBotKey key = new RunningBotKey(broker, ticker);
    if (runningBots.containsKey(key)) {
      throw new LiveBotRunningException(broker, ticker);
    }
    AssignedSettings assigned = tickerSettings.getAssignedSettings(broker, ticker);
    Broker tradingBroker = candleHistory.tradingBroker(broker, ticker);
    LiveTradingAccount account =
        new LiveTradingAccount(
            ticker,
            tradingBroker,
            new PositionTracker(broker, ticker),
            positionStore,
            assigned.budget().orElseThrow());
    LiveSignalSource signalSource = warmedUpSignalSource(broker, ticker, assigned.settings());
    long runId =
        runStore.insertStartedRun(
            new RunStart(
                BotMode.TRADING,
                broker,
                ticker,
                clock.instant(),
                Optional.empty(),
                Optional.empty(),
                currentValue(tradingBroker, account),
                assigned.algorithmId(),
                assigned.source()));
    account.startRun(runId);
    LiveTradingBot bot =
        new LiveTradingBot(
            tradingBroker,
            runId,
            account,
            orderChecker,
            signalSource,
            assigned.algorithmId(),
            evaluationQueue(broker),
            clock);
    runningBots.put(key, bot);
    bot.startListening();
    log.info("{} {}: live run {} started with {}", broker, ticker, runId, assigned.settings());
    return runId;
  }

  /**
   * Starts the bot of every ticker that has settings and doesn't run yet. A ticker that can't start
   * is logged and skipped, so the others still start.
   *
   * @throws TradingModeRequiredException unless the bot is in trading mode
   */
  public synchronized LiveBotStatusList startAllBots(String broker) {
    if (botModeService.botMode() != BotMode.TRADING) {
      throw new TradingModeRequiredException();
    }
    tickerSettings.getTickerSettings(broker).tickers().stream()
        .filter(settings -> settings.assigned().isPresent())
        .map(TickerSettings::ticker)
        .filter(ticker -> !runningBots.containsKey(new RunningBotKey(broker, ticker)))
        .forEach(ticker -> startBotOrLog(broker, ticker));
    return getBotStatuses(broker);
  }

  /**
   * Stops the bot and records the end as STOPPED. The ticker's budget becomes the bot's value, so
   * it keeps its profit or loss.
   *
   * @return the id of the stopped run
   * @throws LiveBotNotRunningException if the ticker's bot doesn't run
   */
  public synchronized long stopBot(String broker, String ticker) {
    LiveTradingBot bot = runningBot(broker, ticker);
    runningBots.remove(new RunningBotKey(broker, ticker));
    RunEnd end = bot.stop();
    runStore.finishRun(bot.runId(), end);
    keepBudget(broker, ticker, end.finalValue().orElseGet(bot::cash));
    log.info("{} {}: live run {} stopped, {}", broker, ticker, bot.runId(), end.counts());
    return bot.runId();
  }

  public synchronized void stopAllBots(String broker) {
    runningBotsOf(broker).forEach(key -> stopBot(key.broker(), key.ticker()));
  }

  /** On shutdown, running bots are stopped and recorded like a stop on request. */
  @PreDestroy
  public synchronized void stopAllBots() {
    List.copyOf(runningBots.keySet()).forEach(this::stopBotOnShutdown);
    workerPools.values().forEach(EvaluationWorkerPool::close);
    workerPools.clear();
  }

  /**
   * Saves new settings for the running bot: checks them and warms a new generator up, then ends the
   * current run and starts a new one with the new algorithm variant, in one transaction. On any
   * failure the bot keeps its run and settings. The ticker keeps the new settings for next time.
   *
   * @throws LiveBotNotRunningException if the ticker's bot doesn't run
   */
  public synchronized LiveBotStatus updateCurrentParameters(
      String broker, String ticker, AlgorithmSettingsRequest request) {
    LiveTradingBot bot = runningBot(broker, ticker);
    TradingAlgorithm algorithm = algorithms.get(request.algorithmType());
    LiveSignalSource signalSource =
        warmedUpSignalSource(broker, ticker, algorithm.resolveSettings(request));
    long algorithmId = settingsService.saveSettings(signalSource.settings());
    long previousRunId = bot.runId();
    synchronized (bot) {
      RunEnd end = bot.runEnd(RunStatus.COMPLETED);
      long nextRunId = runStore.replaceRun(previousRunId, end, nextRunStart(bot, end, algorithmId));
      bot.startNextRun(nextRunId, signalSource, algorithmId);
    }
    keepSettings(broker, ticker, request);
    log.info(
        "{} {}: live run {} replaced by run {} with {}",
        broker,
        ticker,
        previousRunId,
        bot.runId(),
        signalSource.settings());
    return bot.status();
  }

  /**
   * Stops placing orders, stop loss and take profit included, until resumed. Pausing a paused bot
   * changes nothing.
   *
   * @throws LiveBotNotRunningException if the ticker's bot doesn't run
   */
  public synchronized LiveBotStatus pauseBot(String broker, String ticker) {
    return setBotPaused(broker, ticker, true);
  }

  /**
   * Places orders again from the next price. Resuming a running bot changes nothing.
   *
   * @throws LiveBotNotRunningException if the ticker's bot doesn't run
   */
  public synchronized LiveBotStatus resumeBot(String broker, String ticker) {
    return setBotPaused(broker, ticker, false);
  }

  public Optional<LiveBotStatus> getBotStatus(String broker, String ticker) {
    return findRunningBot(broker, ticker).map(LiveTradingBot::status);
  }

  /** The broker's running bots, by ticker. */
  public LiveBotStatusList getBotStatuses(String broker) {
    return new LiveBotStatusList(
        runningBotsOf(broker).stream()
            .map(runningBots::get)
            .map(LiveTradingBot::status)
            .sorted(Comparator.comparing(LiveBotStatus::ticker))
            .toList());
  }

  public boolean isAnyBotRunning(String broker) {
    return !runningBotsOf(broker).isEmpty();
  }

  /**
   * @throws LiveBotRunningException while any live bot runs
   */
  public synchronized void switchBotMode(BotMode botMode) {
    Optional<RunningBotKey> running = runningBots.keySet().stream().findFirst();
    if (running.isPresent()) {
      throw new LiveBotRunningException(running.get().broker(), running.get().ticker());
    }
    botModeService.setBotMode(botMode);
  }

  Optional<LiveTradingBot> findRunningBot(String broker, String ticker) {
    return Optional.ofNullable(runningBots.get(new RunningBotKey(broker, ticker)));
  }

  private List<RunningBotKey> runningBotsOf(String broker) {
    return runningBots.keySet().stream().filter(key -> key.broker().equals(broker)).toList();
  }

  private void startBotOrLog(String broker, String ticker) {
    try {
      startBot(broker, ticker);
    } catch (RuntimeException e) {
      log.error("{} {}: the live bot could not start", broker, ticker, e);
    }
  }

  /** The run is recorded either way; a budget that can't be kept is logged. */
  private void keepBudget(String broker, String ticker, BigDecimal value) {
    try {
      tickerSettings.updateBudget(broker, ticker, value);
    } catch (RuntimeException e) {
      log.error("{} {}: the budget {} could not be kept", broker, ticker, value, e);
    }
  }

  /** The bot already trades with the new settings; settings that can't be kept are logged. */
  private void keepSettings(String broker, String ticker, AlgorithmSettingsRequest request) {
    try {
      tickerSettings.assignSettings(broker, ticker, request, Optional.empty());
    } catch (RuntimeException e) {
      log.error("{} {}: the new settings could not be kept for next time", broker, ticker, e);
    }
  }

  private LiveEvaluationQueue evaluationQueue(String broker) {
    return evaluationQueues.computeIfAbsent(broker, this::startEvaluationQueue);
  }

  private LiveEvaluationQueue startEvaluationQueue(String broker) {
    LiveEvaluationQueue queue = new LiveEvaluationQueue(broker);
    int workerCount = Math.max(MIN_EVALUATION_WORKERS, Runtime.getRuntime().availableProcessors());
    workerPools.put(broker, new EvaluationWorkerPool(queue, workerCount));
    log.info("{}: evaluating live prices on {} workers", broker, workerCount);
    return queue;
  }

  private void stopBotOnShutdown(RunningBotKey key) {
    try {
      stopBot(key.broker(), key.ticker());
    } catch (RuntimeException e) {
      log.error("{} {}: stopping the live bot on shutdown failed", key.broker(), key.ticker(), e);
    }
  }

  private LiveBotStatus setBotPaused(String broker, String ticker, boolean paused) {
    LiveTradingBot bot = runningBot(broker, ticker);
    bot.setPaused(paused);
    log.info("{} {}: live run {} paused: {}", broker, ticker, bot.runId(), paused);
    return bot.status();
  }

  private LiveTradingBot runningBot(String broker, String ticker) {
    return findRunningBot(broker, ticker)
        .orElseThrow(() -> new LiveBotNotRunningException(broker, ticker));
  }

  /** The bot's own value at the latest price, or its cash while there is no price yet. */
  private static BigDecimal currentValue(Broker broker, LiveTradingAccount account) {
    return broker
        .tickerPrices()
        .get(account.ticker())
        .map(TickerQuote::lastPrice)
        .map(account::portfolioValue)
        .orElseGet(account::ownCash);
  }

  private RunStart nextRunStart(LiveTradingBot bot, RunEnd end, long algorithmId) {
    return new RunStart(
        BotMode.TRADING,
        bot.brokerName(),
        bot.ticker(),
        end.finishedAt(),
        Optional.empty(),
        Optional.empty(),
        end.finalValue().orElseGet(bot::cash),
        algorithmId,
        SettingsSource.MANUAL);
  }

  private LiveSignalSource warmedUpSignalSource(
      String broker, String ticker, AlgorithmSettings settings) {
    TradingAlgorithm algorithm = algorithms.get(settings.algorithmType());
    LiveSignalSource signalSource =
        new LiveSignalSource(candleHistory, broker, ticker, algorithm, settings);
    signalSource.warmUp(clock.instant());
    return signalSource;
  }
}
