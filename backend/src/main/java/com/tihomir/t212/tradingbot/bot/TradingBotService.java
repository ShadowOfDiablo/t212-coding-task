package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Starts, changes and stops the live bots, one per broker, and records their runs. A live bot
 * trades on the exchange's demo account; every order goes through {@link OrderCheckerService}.
 */
@Service
public class TradingBotService {

  private static final Logger log = LoggerFactory.getLogger(TradingBotService.class);

  private final BotModeService botModeService;
  private final AlgorithmRegistryService algorithms;
  private final AlgorithmSettingsService settingsService;
  private final CandleHistoryService candleHistory;
  private final OrderCheckerService orderChecker;
  private final AlgorithmRunStore runStore;
  private final Clock clock;
  private final Map<String, LiveTradingBot> runningBots = new ConcurrentHashMap<>();

  public TradingBotService(
      BotModeService botModeService,
      AlgorithmRegistryService algorithms,
      AlgorithmSettingsService settingsService,
      CandleHistoryService candleHistory,
      OrderCheckerService orderChecker,
      AlgorithmRunStore runStore,
      Clock clock) {
    this.botModeService = botModeService;
    this.algorithms = algorithms;
    this.settingsService = settingsService;
    this.candleHistory = candleHistory;
    this.orderChecker = orderChecker;
    this.runStore = runStore;
    this.clock = clock;
  }

  /**
   * Checks the request, warms the algorithm up from the candle store, records the start and starts
   * listening to live prices. Nothing is recorded when a check fails.
   *
   * @return the id of the recorded run
   * @throws TradingModeRequiredException unless the bot is in trading mode
   * @throws LiveBotRunningException if the broker's bot already runs
   */
  public synchronized long startBot(LiveRunRequest request) {
    if (botModeService.botMode() != BotMode.TRADING) {
      throw new TradingModeRequiredException();
    }
    if (runningBots.containsKey(request.broker())) {
      throw new LiveBotRunningException(request.broker());
    }
    Broker broker = candleHistory.tradingBroker(request.broker(), request.ticker());
    LiveTradingAccount account = new LiveTradingAccount(request.ticker(), broker.tradingGateway());
    LiveSignalSource signalSource =
        warmedUpSignalSource(broker.name(), request.ticker(), request.settings());
    BigDecimal startingValue = account.cash();
    long algorithmId = settingsService.saveSettings(signalSource.settings());
    long runId =
        runStore.insertStartedRun(
            new RunStart(
                BotMode.TRADING,
                broker.name(),
                request.ticker(),
                clock.instant(),
                Optional.empty(),
                Optional.empty(),
                startingValue,
                algorithmId));
    LiveTradingBot bot =
        new LiveTradingBot(broker, runId, account, orderChecker, signalSource, algorithmId, clock);
    runningBots.put(broker.name(), bot);
    bot.startListening();
    log.info(
        "{} {}: live run {} started with {}",
        broker.name(),
        request.ticker(),
        runId,
        signalSource.settings());
    return runId;
  }

  /**
   * Stops the bot and records the end as STOPPED.
   *
   * @return the id of the stopped run
   * @throws LiveBotNotRunningException if the broker's bot doesn't run
   */
  public synchronized long stopBot(String broker) {
    LiveTradingBot bot = runningBot(broker);
    runningBots.remove(broker);
    RunEnd end = bot.stop();
    runStore.finishRun(bot.runId(), end);
    log.info("{} {}: live run {} stopped, {}", broker, bot.ticker(), bot.runId(), end.counts());
    return bot.runId();
  }

  /**
   * Saves new settings for the running bot: checks them and warms a new generator up, then ends the
   * current run and starts a new one with the new algorithm variant, in one transaction. On any
   * failure the bot keeps its run and settings.
   *
   * @throws LiveBotNotRunningException if the broker's bot doesn't run
   */
  public synchronized LiveBotStatus updateCurrentParameters(
      String broker, AlgorithmSettingsRequest request) {
    LiveTradingBot bot = runningBot(broker);
    LiveSignalSource signalSource = warmedUpSignalSource(broker, bot.ticker(), request);
    long algorithmId = settingsService.saveSettings(signalSource.settings());
    long previousRunId = bot.runId();
    synchronized (bot) {
      RunEnd end = bot.runEnd(RunStatus.COMPLETED);
      long nextRunId = runStore.replaceRun(previousRunId, end, nextRunStart(bot, end, algorithmId));
      bot.startNextRun(nextRunId, signalSource, algorithmId);
    }
    log.info(
        "{} {}: live run {} replaced by run {} with {}",
        broker,
        bot.ticker(),
        previousRunId,
        bot.runId(),
        signalSource.settings());
    return bot.status();
  }

  public Optional<LiveBotStatus> getBotStatus(String broker) {
    return findRunningBot(broker).map(LiveTradingBot::status);
  }

  /**
   * @throws LiveBotRunningException while any live bot runs
   */
  public synchronized void switchBotMode(BotMode botMode) {
    Optional<String> brokerWithBot = runningBots.keySet().stream().findFirst();
    if (brokerWithBot.isPresent()) {
      throw new LiveBotRunningException(brokerWithBot.get());
    }
    botModeService.setBotMode(botMode);
  }

  /** On shutdown, running bots are stopped and recorded like a stop on request. */
  @PreDestroy
  public synchronized void stopAllBots() {
    List.copyOf(runningBots.keySet()).forEach(this::stopBotOnShutdown);
  }

  Optional<LiveTradingBot> findRunningBot(String broker) {
    return Optional.ofNullable(runningBots.get(broker));
  }

  private void stopBotOnShutdown(String broker) {
    try {
      stopBot(broker);
    } catch (RuntimeException e) {
      log.error("{}: stopping the live bot on shutdown failed", broker, e);
    }
  }

  private LiveTradingBot runningBot(String broker) {
    return findRunningBot(broker).orElseThrow(() -> new LiveBotNotRunningException(broker));
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
        algorithmId);
  }

  private LiveSignalSource warmedUpSignalSource(
      String broker, String ticker, AlgorithmSettingsRequest request) {
    TradingAlgorithm algorithm = algorithms.get(request.algorithmType());
    AlgorithmSettings settings = algorithm.resolveSettings(request);
    LiveSignalSource signalSource =
        new LiveSignalSource(candleHistory, broker, ticker, algorithm, settings);
    signalSource.warmUp(clock.instant());
    return signalSource;
  }
}
