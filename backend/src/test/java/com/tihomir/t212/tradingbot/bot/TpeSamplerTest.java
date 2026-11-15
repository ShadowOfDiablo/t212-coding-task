package com.tihomir.t212.tradingbot.bot;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class TpeSamplerTest {

  private static final int TRIAL_COUNT = 40;
  private static final int PEAK_TIME_PERIOD = 148;
  private static final double BEST_TIMEFRAME_INDEX = 2;
  private static final double TIMEFRAME_BONUS = 1000;
  private static final long FIRST_SEED = 1;
  private static final long SEED_COUNT = 20;
  private static final String OPTIONAL_SETTING = "stopLossPercent";
  private static final TuningDimension TIME_PERIOD =
      new TuningDimension("timePeriod", TuningDimensionKind.WHOLE_NUMBER, 5, 300);
  private static final TuningDimension ORDER_SIZE =
      new TuningDimension("orderSizePercent", TuningDimensionKind.NUMBER, 1, 100);
  private static final TuningDimension TIMEFRAME =
      new TuningDimension("timeframe", TuningDimensionKind.CATEGORY, 0, 3);
  private static final TuningDimension STOP_LOSS =
      new TuningDimension(OPTIONAL_SETTING, TuningDimensionKind.NUMBER, 1, 10);
  private static final List<TuningDimension> DIMENSIONS =
      List.of(TIME_PERIOD, ORDER_SIZE, TIMEFRAME, STOP_LOSS);
  private static final AlgorithmSettingsRequest SETTINGS =
      new AlgorithmSettingsRequest(
          AlgorithmType.SIMPLE_MOVING_AVERAGE, CandleInterval.FIVE_MINUTES, true, Map.of());

  @Test
  void drawsEveryValueInItsRangeWholeNumbersWholeAndCategoriesFromTheirChoices() {
    TpeSampler sampler = new TpeSampler(DIMENSIONS, new Random(FIRST_SEED));
    List<TuningTrial> trials = new ArrayList<>();

    for (int trialNumber = 0; trialNumber < TRIAL_COUNT; trialNumber++) {
      TuningPoint point = sampler.nextPoint(trials);
      DIMENSIONS.forEach(dimension -> assertInRange(dimension, point));
      assertThat(point.value(TIME_PERIOD.name()).orElseThrow() % 1).isZero();
      assertThat(point.value(TIMEFRAME.name()).orElseThrow() % 1).isZero();
      trials.add(trial(point));
    }
  }

  @Test
  void skipsTheValuesATryDidNotUse() {
    TpeSampler sampler = new TpeSampler(DIMENSIONS, new Random(FIRST_SEED));
    List<TuningTrial> trials = new ArrayList<>();

    for (int trialNumber = 0; trialNumber < TRIAL_COUNT; trialNumber++) {
      TuningPoint point = sampler.nextPoint(trials);
      assertInRange(STOP_LOSS, point);
      trials.add(trial(point.without(Set.of(OPTIONAL_SETTING))));
    }
  }

  @Test
  void getsCloserToThePeakThanRandomTries() {
    double tpeDistance =
        LongStream.range(FIRST_SEED, SEED_COUNT).mapToDouble(this::tpeDistance).sum();
    double randomDistance =
        LongStream.range(FIRST_SEED, SEED_COUNT).mapToDouble(this::randomDistance).sum();

    assertThat(tpeDistance).isLessThan(randomDistance);
  }

  private double tpeDistance(long seed) {
    TpeSampler sampler = new TpeSampler(DIMENSIONS, new Random(seed));
    List<TuningTrial> trials = new ArrayList<>();
    for (int trialNumber = 0; trialNumber < TRIAL_COUNT; trialNumber++) {
      trials.add(trial(sampler.nextPoint(trials)));
    }
    return bestDistance(trials);
  }

  private double randomDistance(long seed) {
    TpeSampler sampler = new TpeSampler(DIMENSIONS, new Random(seed));
    List<TuningTrial> trials = new ArrayList<>();
    for (int trialNumber = 0; trialNumber < TRIAL_COUNT; trialNumber++) {
      trials.add(trial(sampler.nextPoint(List.of())));
    }
    return bestDistance(trials);
  }

  private static double bestDistance(List<TuningTrial> trials) {
    TuningTrial best =
        trials.stream().max(Comparator.comparingDouble(TuningTrial::score)).orElseThrow();
    return Math.abs(best.point().value(TIME_PERIOD.name()).orElseThrow() - PEAK_TIME_PERIOD);
  }

  private static TuningTrial trial(TuningPoint point) {
    return new TuningTrial(point, SETTINGS, score(point));
  }

  /** Peaks at timePeriod 148 with the third timeframe; the other settings don't matter. */
  private static double score(TuningPoint point) {
    double distance = point.value(TIME_PERIOD.name()).orElseThrow() - PEAK_TIME_PERIOD;
    double timeframeBonus = 0;
    if (point.value(TIMEFRAME.name()).orElseThrow() == BEST_TIMEFRAME_INDEX) {
      timeframeBonus = TIMEFRAME_BONUS;
    }
    return timeframeBonus - distance * distance;
  }

  private static void assertInRange(TuningDimension dimension, TuningPoint point) {
    assertThat(point.value(dimension.name()))
        .hasValueSatisfying(
            value -> assertThat(value).isBetween(dimension.minimum(), dimension.maximum()));
  }
}
