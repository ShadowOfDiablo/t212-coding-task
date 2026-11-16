package com.tihomir.t212.tradingbot.bot.training;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.bot.exception.TrainingAlreadyRunningException;
import com.tihomir.t212.tradingbot.bot.exception.TrainingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.run.AlgorithmRunStore;
import com.tihomir.t212.tradingbot.bot.run.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.run.RunCounts;
import com.tihomir.t212.tradingbot.bot.run.RunEnd;
import com.tihomir.t212.tradingbot.bot.run.RunStart;
import com.tihomir.t212.tradingbot.bot.run.RunStatus;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.run.UnrecordedRun;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryRequest;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.gateway.VirtualTradingGateway;
import com.tihomir.t212.tradingbot.order.model.TickerTradingRules;
import com.tihomir.t212.tradingbot.position.PositionTracker;
import com.tihomir.t212.tradingbot.position.PositionWriteList;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs an algorithm over stored candles on a fresh virtual account and records the run: its start,
 * then its end (completed or failed). One training run or tuning at a time.
 */
@Service
public class TrainingService {

  private static final Logger log = LoggerFactory.getLogger(TrainingService.class);

  /** The run id of a replay that is never recorded; its position writes are thrown away. */
  private static final long UNRECORDED_RUN_ID = 0;

  private final BotModeService botModeService;
  private final AlgorithmRegistryService algorithms;
  private final AlgorithmSettingsService settingsService;
  private final CandleHistoryService candleHistory;
  private final OrderCheckerService orderChecker;
  private final AlgorithmRunStore runStore;
  private final TrainingLock trainingLock;
  private final Clock clock;

  public TrainingService(
      BotModeService botModeService,
      AlgorithmRegistryService algorithms,
      AlgorithmSettingsService settingsService,
      CandleHistoryService candleHistory,
      OrderCheckerService orderChecker,
      AlgorithmRunStore runStore,
      TrainingLock trainingLock,
      Clock clock) {
    this.botModeService = botModeService;
    this.algorithms = algorithms;
    this.settingsService = settingsService;
    this.candleHistory = candleHistory;
    this.orderChecker = orderChecker;
    this.runStore = runStore;
    this.trainingLock = trainingLock;
    this.clock = clock;
  }

  /**
   * Checks everything that can be checked, then records the start, replays the candles and records
   * the end.
   *
   * @return the id of the recorded run
   * @throws TrainingModeRequiredException unless the bot is in training mode
   * @throws TrainingAlreadyRunningException while another training run or a tuning goes on
   */
  public long runTraining(TrainingRunRequest request) {
    checkTrainingMode();
    TradingAlgorithm algorithm = algorithms.get(request.settings().algorithmType());
    TrainingSetup setup =
        new TrainingSetup(
            request,
            algorithm,
            algorithm.resolveSettings(request.settings()),
            tradingRules(request.broker(), request.ticker()));
    trainingLock.lock();
    try {
      return recordTrainingRun(setup, SettingsSource.MANUAL);
    } finally {
      trainingLock.unlock();
    }
  }

  /** True while a training run or a tuning goes on: its settings can't change until it ends. */
  public boolean isTrainingRunning() {
    return trainingLock.isLocked();
  }

  /**
   * @throws TrainingModeRequiredException unless the bot is in training mode
   */
  public void checkTrainingMode() {
    if (botModeService.botMode() != BotMode.TRAINING) {
      throw new TrainingModeRequiredException();
    }
  }

  public TickerTradingRules tradingRules(String broker, String ticker) {
    return candleHistory.tradingBroker(broker, ticker).tradingGateway().tradingRules(ticker);
  }

  /**
   * @throws IllegalArgumentException if the request's range holds no closed candle
   */
  public CandleList closedCandles(TrainingRunRequest request, CandleInterval timeframe) {
    CandleList candles =
        candleHistory.getCandles(
            new CandleHistoryRequest(
                request.broker(), request.ticker(), timeframe, request.from(), request.to()));
    if (candles.candles().isEmpty()) {
      throw new IllegalArgumentException("There are no closed candles in this range");
    }
    return candles;
  }

  /** Replays the candles on a fresh virtual account and records nothing. */
  public BigDecimal replayFinalValue(TrainingSetup setup, CandleList candles) {
    TrainingReplay replay = newReplay(setup, UNRECORDED_RUN_ID);
    replay.replay(candles.candles());
    return replay.finalValue();
  }

  /** Replays on a fresh virtual account, with points and counts, and records nothing. */
  public UnrecordedRun replayWithoutRecording(TrainingSetup setup) {
    RunEnd end = replay(setup, UNRECORDED_RUN_ID);
    return new UnrecordedRun(end.finalValue().orElseThrow(), end.counts(), end.points());
  }

  /**
   * Records the start, replays the candles and records the end. A failure is recorded as FAILED and
   * thrown again. The caller holds the training lock.
   *
   * @return the id of the recorded run
   */
  public long recordTrainingRun(TrainingSetup setup, SettingsSource settingsSource) {
    TrainingRunRequest request = setup.request();
    long algorithmId = settingsService.saveSettings(setup.settings());
    long runId =
        runStore.insertStartedRun(
            new RunStart(
                BotMode.TRAINING,
                request.broker(),
                request.ticker(),
                clock.instant(),
                Optional.of(request.from()),
                Optional.of(request.to()),
                request.startingBalance(),
                algorithmId,
                settingsSource));
    try {
      RunEnd end = replay(setup, runId);
      runStore.finishRun(runId, end);
      log.info(
          "Training run {}: {} {} with {}, final value {}",
          runId,
          request.ticker(),
          setup.settings().timeframe(),
          setup.algorithm().type(),
          end.finalValue().orElseThrow());
      return runId;
    } catch (RuntimeException e) {
      recordFailure(runId, e);
      throw e;
    }
  }

  private RunEnd replay(TrainingSetup setup, long runId) {
    CandleList candles = closedCandles(setup.request(), setup.settings().timeframe());
    TrainingReplay replay = newReplay(setup, runId);
    PortfolioPointList points = replay.replay(candles.candles());
    return new RunEnd(
        RunStatus.COMPLETED,
        clock.instant(),
        Optional.of(replay.finalValue()),
        replay.counts(),
        Optional.empty(),
        points,
        replay.positionWrites());
  }

  private TrainingReplay newReplay(TrainingSetup setup, long runId) {
    TrainingRunRequest request = setup.request();
    AlgorithmSettings settings = setup.settings();
    VirtualTradingGateway account =
        new VirtualTradingGateway(
            request.ticker(), request.startingBalance(), setup.tradingRules());
    return new TrainingReplay(
        setup.algorithm().createSignalGenerator(settings),
        account,
        orderChecker,
        settings,
        positionTracker(request, runId));
  }

  private static PositionTracker positionTracker(TrainingRunRequest request, long runId) {
    PositionTracker tracker = new PositionTracker(request.broker(), request.ticker());
    tracker.startRun(runId);
    return tracker;
  }

  private void recordFailure(long runId, RuntimeException failure) {
    log.error("Training run {} failed: {}", runId, failure.getMessage(), failure);
    try {
      runStore.finishRun(
          runId,
          new RunEnd(
              RunStatus.FAILED,
              clock.instant(),
              Optional.empty(),
              RunCounts.NONE,
              Optional.of(String.valueOf(failure.getMessage())),
              PortfolioPointList.NONE,
              PositionWriteList.NONE));
    } catch (RuntimeException recordingFailure) {
      log.error("Recording the failure of training run {} failed", runId, recordingFailure);
      failure.addSuppressed(recordingFailure);
    }
  }
}
