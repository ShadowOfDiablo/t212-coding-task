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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Runs an algorithm over stored candles on a fresh virtual account, in the background on the
 * training thread, and records the run: its start, then its end (completed or failed). One training
 * run at a time.
 */
@Service
public class TrainingService implements TrainingActivity {

  private static final Logger log = LoggerFactory.getLogger(TrainingService.class);

  private final BotModeService botModeService;
  private final AlgorithmRegistryService algorithms;
  private final AlgorithmSettingsService settingsService;
  private final CandleHistoryService candleHistory;
  private final OrderCheckerService orderChecker;
  private final AlgorithmRunStore runStore;
  private final Clock clock;
  private final ExecutorService trainingThread;
  private final AtomicBoolean trainingRunning = new AtomicBoolean();
  private final AtomicReference<Optional<RunningTraining>> latestTraining =
      new AtomicReference<>(Optional.empty());

  @Autowired
  public TrainingService(
      BotModeService botModeService,
      AlgorithmRegistryService algorithms,
      AlgorithmSettingsService settingsService,
      CandleHistoryService candleHistory,
      OrderCheckerService orderChecker,
      AlgorithmRunStore runStore,
      Clock clock) {
    this(
        botModeService,
        algorithms,
        settingsService,
        candleHistory,
        orderChecker,
        runStore,
        clock,
        Executors.newSingleThreadExecutor(TrainingService::newDaemonThread));
  }

  TrainingService(
      BotModeService botModeService,
      AlgorithmRegistryService algorithms,
      AlgorithmSettingsService settingsService,
      CandleHistoryService candleHistory,
      OrderCheckerService orderChecker,
      AlgorithmRunStore runStore,
      Clock clock,
      ExecutorService trainingThread) {
    this.botModeService = botModeService;
    this.algorithms = algorithms;
    this.settingsService = settingsService;
    this.candleHistory = candleHistory;
    this.orderChecker = orderChecker;
    this.runStore = runStore;
    this.clock = clock;
    this.trainingThread = trainingThread;
  }

  /**
   * Checks everything that can be checked, records the start and hands the replay to the training
   * thread. Nothing is recorded when a check fails.
   *
   * @return the id of the recorded run, before the replay has ended
   * @throws TrainingModeRequiredException unless the bot is in training mode
   * @throws TrainingAlreadyRunningException while another training run goes on
   * @throws RejectedExecutionException when the training thread is shut down; the run is then
   *     recorded as failed
   */
  public long startTraining(TrainingRunRequest request) {
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
    RunningTraining training;
    try {
      training = recordStart(request, setup);
    } catch (RuntimeException e) {
      trainingRunning.set(false);
      throw e;
    }
    latestTraining.set(Optional.of(training));
    try {
      trainingThread.execute(() -> replayAndRecordEnd(setup, training));
    } catch (RejectedExecutionException e) {
      recordFailure(training.runId(), e);
      endTraining(training);
      throw e;
    }
    return training.runId();
  }

  /** False while a training run goes on: its settings can't change until it ends. */
  public boolean areParametersEditable() {
    return !trainingRunning.get();
  }

  @Override
  public boolean isTrainingGoing() {
    return trainingRunning.get();
  }

  /** The training going on, or the last one; empty before the first. */
  public Optional<RunningTraining> latestTraining() {
    return latestTraining.get();
  }

  private RunningTraining recordStart(TrainingRunRequest request, TrainingSetup setup) {
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
    return new RunningTraining(request.broker(), runId, request.startingBalance());
  }

  /** Runs on the training thread; every failure is recorded with the run, never thrown. */
  private void replayAndRecordEnd(TrainingSetup setup, RunningTraining training) {
    long runId = training.runId();
    try {
      RunEnd end = replay(setup, training);
      runStore.finishRun(runId, end);
      log.info(
          "Training run {}: {} {} with {}, final value {}",
          runId,
          setup.request().ticker(),
          setup.settings().timeframe(),
          setup.algorithm().type(),
          end.finalValue().orElseThrow());
    } catch (RuntimeException e) {
      recordFailure(runId, e);
    } finally {
      endTraining(training);
    }
  }

  private void endTraining(RunningTraining training) {
    training.markFinished();
    trainingRunning.set(false);
  }

  private RunEnd replay(TrainingSetup setup, RunningTraining training) {
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
            positionTracker(request, training.runId()));
    PortfolioPointList points = replay.replay(candles, training);
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
    }
  }

  private static Thread newDaemonThread(Runnable runnable) {
    Thread thread = new Thread(runnable, "training");
    thread.setDaemon(true);
    return thread;
  }
}
