package com.tihomir.t212.tradingbot.bot.tuning;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.bot.exception.NoSuccessfulTuningTrialException;
import com.tihomir.t212.tradingbot.bot.exception.TrainingAlreadyRunningException;
import com.tihomir.t212.tradingbot.bot.exception.TrainingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.run.UnrecordedRun;
import com.tihomir.t212.tradingbot.bot.training.TrainingLock;
import com.tihomir.t212.tradingbot.bot.training.TrainingRunRequest;
import com.tihomir.t212.tradingbot.bot.training.TrainingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Finds good settings automatically: replays many tries in memory on the tune range, chosen with
 * TPE, and records only the best one, replayed over the whole range, as a training run with
 * AUTOMATIC settings. Shares the training lock, so it never runs beside a training run.
 */
@Service
public class TuningService {

  private static final Logger log = LoggerFactory.getLogger(TuningService.class);

  private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
  private static final int PERCENT_SCALE = 4;

  private final TrainingService trainingService;
  private final AlgorithmRegistryService algorithms;
  private final TrainingLock trainingLock;
  private final AtomicReference<Optional<TuningProgress>> progress =
      new AtomicReference<>(Optional.empty());

  public TuningService(
      TrainingService trainingService,
      AlgorithmRegistryService algorithms,
      TrainingLock trainingLock) {
    this.trainingService = trainingService;
    this.algorithms = algorithms;
    this.trainingLock = trainingLock;
  }

  /**
   * Checks everything that can be checked first; a failed try is logged and the next one goes on.
   *
   * @throws TrainingModeRequiredException unless the bot is in training mode
   * @throws TrainingAlreadyRunningException while a training run or another tuning goes on
   * @throws com.tihomir.t212.tradingbot.algorithm.exception.InvalidAlgorithmSettingsException for
   *     base settings or ranges that aren't allowed
   * @throws NoSuccessfulTuningTrialException if every try failed; nothing is recorded then
   */
  public TuningResult runTuning(TuningRequest request) {
    trainingService.checkTrainingMode();
    TrainingRunRequest training = request.training();
    TradingAlgorithm algorithm = algorithms.get(training.settings().algorithmType());
    algorithm.resolveSettings(training.settings());
    TuningSession session =
        new TuningSession(
            request,
            algorithm,
            trainingService.tradingRules(training.broker(), training.ticker()),
            trainingService);
    trainingLock.lock();
    try {
      return tunedRun(request, session);
    } finally {
      progress.set(Optional.empty());
      trainingLock.unlock();
    }
  }

  /** The share of tries done while a tuning goes on; empty otherwise. */
  public Optional<Integer> progressPercent() {
    return progress.get().map(TuningProgress::percentDone);
  }

  private TuningResult tunedRun(TuningRequest request, TuningSession session) {
    TpeSampler sampler = new TpeSampler(session.dimensions(), random(request));
    List<TuningTrial> trials = new ArrayList<>();
    int maxTrials = request.maxTrials();
    progress.set(Optional.of(new TuningProgress(0, maxTrials)));
    for (int trialNumber = 0; trialNumber < maxTrials; trialNumber++) {
      trials.add(session.scoredTrial(sampler.nextPoint(trials)));
      progress.set(Optional.of(new TuningProgress(trials.size(), maxTrials)));
    }
    TuningTrial best =
        trials.stream()
            .filter(TuningTrial::isSuccessful)
            .max(Comparator.comparingDouble(TuningTrial::score))
            .orElseThrow(() -> new NoSuccessfulTuningTrialException(trials.size()));
    long runId =
        trainingService.recordTrainingRun(
            session.fullRangeSetup(best.settings()), SettingsSource.AUTOMATIC);
    BigDecimal bestTuneReturnPercent =
        BigDecimal.valueOf(best.score())
            .multiply(PERCENT)
            .setScale(PERCENT_SCALE, RoundingMode.HALF_EVEN);
    log.info(
        "Tuning {} {}: best of {} tries returned {}% with {} {}, recorded as run {}",
        request.training().broker(),
        request.training().ticker(),
        trials.size(),
        bestTuneReturnPercent,
        best.settings().timeframe().code(),
        best.settings().values(),
        runId);
    return new TuningResult(runId, bestTuneReturnPercent, trials.size(), baseRun(request, session));
  }

  /**
   * The base settings replayed over the whole range, to compare the tuned run with. A failure only
   * loses the comparison: the tuned run is already recorded.
   */
  private Optional<UnrecordedRun> baseRun(TuningRequest request, TuningSession session) {
    try {
      return Optional.of(
          trainingService.replayWithoutRecording(
              session.fullRangeSetup(request.training().settings())));
    } catch (RuntimeException e) {
      log.warn(
          "Tuning {} {}: replaying the base settings for the comparison failed: {}",
          request.training().broker(),
          request.training().ticker(),
          e.getMessage(),
          e);
      return Optional.empty();
    }
  }

  private static Random random(TuningRequest request) {
    return new Random(request.getSeed());
  }
}
