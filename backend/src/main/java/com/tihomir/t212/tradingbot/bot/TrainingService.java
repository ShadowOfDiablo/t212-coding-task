package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryRequest;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.VirtualTradingGateway;
import com.tihomir.t212.tradingbot.position.PositionTracker;
import com.tihomir.t212.tradingbot.position.PositionWriteList;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs an algorithm over stored candles on a fresh virtual account and records the run: its start,
 * then its end (completed or failed). One training run at a time.
 */
@Service
public class TrainingService {

  private static final Logger log = LoggerFactory.getLogger(TrainingService.class);

  private final BotModeService botModeService;
  private final AlgorithmRegistryService algorithms;
  private final AlgorithmSettingsService settingsService;
  private final CandleHistoryService candleHistory;
  private final OrderCheckerService orderChecker;
  private final AlgorithmRunStore runStore;
  private final Clock clock;
  private final AtomicBoolean trainingRunning = new AtomicBoolean();

  public TrainingService(
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
   * Checks everything that can be checked, then records the start, replays the candles and records
   * the end.
   *
   * @return the id of the recorded run
   * @throws TrainingModeRequiredException unless the bot is in training mode
   * @throws TrainingAlreadyRunningException while another training run goes on
   */
  public long runTraining(TrainingRunRequest request) {
    if (botModeService.botMode() != BotMode.TRAINING) {
      throw new TrainingModeRequiredException();
    }
    TradingAlgorithm algorithm = algorithms.get(request.settings().algorithmType());
    TrainingSetup setup =
        new TrainingSetup(
            request,
            algorithm,
            algorithm.resolveSettings(request.settings()),
            candleHistory
                .tradingBroker(request.broker(), request.ticker())
                .tradingGateway()
                .tradingRules(request.ticker()));
    if (!trainingRunning.compareAndSet(false, true)) {
      throw new TrainingAlreadyRunningException();
    }
    try {
      return recordedTraining(setup);
    } finally {
      trainingRunning.set(false);
    }
  }

  /** True while a training run goes on: its settings can't change until it ends. */
  public boolean isTrainingRunning() {
    return trainingRunning.get();
  }

  private long recordedTraining(TrainingSetup setup) {
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
                algorithmId));
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
    TrainingRunRequest request = setup.request();
    AlgorithmSettings settings = setup.settings();
    List<CandleUpdate> candles =
        candleHistory
            .getCandles(
                new CandleHistoryRequest(
                    request.broker(),
                    request.ticker(),
                    settings.timeframe(),
                    request.from(),
                    request.to()))
            .candles();
    if (candles.isEmpty()) {
      throw new IllegalArgumentException("There are no closed candles in this range");
    }
    VirtualTradingGateway account =
        new VirtualTradingGateway(
            request.ticker(), request.startingBalance(), setup.tradingRules());
    TrainingReplay replay =
        new TrainingReplay(
            setup.algorithm().createSignalGenerator(settings),
            account,
            orderChecker,
            settings,
            positionTracker(request, runId));
    PortfolioPointList points = replay.replay(candles);
    return new RunEnd(
        RunStatus.COMPLETED,
        clock.instant(),
        Optional.of(replay.finalValue()),
        replay.counts(),
        Optional.empty(),
        points,
        replay.positionWrites());
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
