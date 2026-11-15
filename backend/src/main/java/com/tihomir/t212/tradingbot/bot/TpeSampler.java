package com.tihomir.t212.tradingbot.bot;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Picks the next try of a tuning with TPE (Tree-structured Parzen Estimator). The first tries are
 * random. After that the tries are split into good (the best {@link #GOOD_FRACTION}) and bad, and
 * for each dimension on its own the candidate with the highest l(x) / g(x) wins: l is the density
 * of the values in the good tries, g of those in the bad tries.
 */
final class TpeSampler {

  static final int RANDOM_TRIALS = 10;

  private static final double GOOD_FRACTION = 0.2;
  private static final int CANDIDATES_PER_TRIAL = 24;
  private static final int PRIOR_WEIGHT = 1;
  private static final double MIN_BANDWIDTH_SHARE = 0.01;
  private static final double SQRT_TWO_PI = Math.sqrt(2 * Math.PI);
  private static final double HALF = 0.5;

  private final List<TuningDimension> dimensions;
  private final Random random;

  TpeSampler(List<TuningDimension> dimensions, Random random) {
    this.dimensions = List.copyOf(dimensions);
    this.random = random;
  }

  TuningPoint nextPoint(List<TuningTrial> trials) {
    if (trials.size() < RANDOM_TRIALS) {
      return new TuningPoint(valuesByDimension(this::randomValue));
    }
    List<TuningTrial> bestFirst =
        trials.stream().sorted(Comparator.comparingDouble(TuningTrial::score).reversed()).toList();
    int goodCount = (int) Math.ceil(GOOD_FRACTION * bestFirst.size());
    List<TuningTrial> good = bestFirst.subList(0, goodCount);
    List<TuningTrial> bad = bestFirst.subList(goodCount, bestFirst.size());
    return new TuningPoint(valuesByDimension(dimension -> bestCandidate(dimension, good, bad)));
  }

  private Map<String, Double> valuesByDimension(TuningDimensionValueDrawer drawer) {
    return dimensions.stream().collect(Collectors.toMap(TuningDimension::name, drawer::drawValue));
  }

  private double bestCandidate(
      TuningDimension dimension, List<TuningTrial> goodTrials, List<TuningTrial> badTrials) {
    List<Double> good = valuesOf(dimension, goodTrials);
    List<Double> bad = valuesOf(dimension, badTrials);
    return IntStream.range(0, CANDIDATES_PER_TRIAL)
        .mapToObj(index -> drawFromDensity(dimension, good))
        .max(Comparator.comparingDouble(value -> densityRatio(dimension, value, good, bad)))
        .orElseThrow();
  }

  private static List<Double> valuesOf(TuningDimension dimension, List<TuningTrial> trials) {
    return trials.stream()
        .flatMap(trial -> trial.point().value(dimension.name()).stream())
        .toList();
  }

  private static double densityRatio(
      TuningDimension dimension, double value, List<Double> good, List<Double> bad) {
    return density(dimension, value, good) / density(dimension, value, bad);
  }

  private double randomValue(TuningDimension dimension) {
    return switch (dimension.kind()) {
      case NUMBER -> dimension.minimum() + random.nextDouble() * dimension.width();
      case WHOLE_NUMBER, CATEGORY -> dimension.minimum() + random.nextInt(choiceCount(dimension));
    };
  }

  /** Draws from the density of the values: around one of them, or from the flat prior. */
  private double drawFromDensity(TuningDimension dimension, List<Double> values) {
    return switch (dimension.kind()) {
      case CATEGORY -> drawCategory(dimension, values);
      case NUMBER -> drawNumber(dimension, values);
      case WHOLE_NUMBER -> clamp(dimension, Math.round(drawNumber(dimension, values)));
    };
  }

  /** Each choice weighs its count among the values, plus one. */
  private double drawCategory(TuningDimension dimension, List<Double> values) {
    int pick = random.nextInt(values.size() + choiceCount(dimension));
    if (pick < values.size()) {
      return values.get(pick);
    }
    return dimension.minimum() + pick - values.size();
  }

  private double drawNumber(TuningDimension dimension, List<Double> values) {
    int pick = random.nextInt(values.size() + PRIOR_WEIGHT);
    if (pick >= values.size()) {
      return randomValue(dimension);
    }
    double drawn = values.get(pick) + random.nextGaussian() * bandwidth(dimension, values);
    return clamp(dimension, drawn);
  }

  /**
   * Categories: (count + 1) / (values + choices). Numbers: a Gaussian bump per value plus a flat
   * prior over the range, averaged.
   */
  private static double density(TuningDimension dimension, double value, List<Double> values) {
    if (dimension.kind() == TuningDimensionKind.CATEGORY) {
      long count = values.stream().filter(other -> other == value).count();
      return (double) (count + PRIOR_WEIGHT) / (values.size() + choiceCount(dimension));
    }
    if (dimension.width() == 0) {
      return 1;
    }
    double bandwidth = bandwidth(dimension, values);
    double bumps =
        values.stream().mapToDouble(center -> normalDensity(value, center, bandwidth)).sum();
    return (bumps + PRIOR_WEIGHT / dimension.width()) / (values.size() + PRIOR_WEIGHT);
  }

  /** Narrower bumps as values pile up: width / (count + 1), at least 1% of the width. */
  private static double bandwidth(TuningDimension dimension, List<Double> values) {
    return Math.max(
        dimension.width() / (values.size() + 1), dimension.width() * MIN_BANDWIDTH_SHARE);
  }

  private static double normalDensity(double value, double mean, double bandwidth) {
    double distance = (value - mean) / bandwidth;
    return Math.exp(-HALF * distance * distance) / (bandwidth * SQRT_TWO_PI);
  }

  private static int choiceCount(TuningDimension dimension) {
    return (int) Math.round(dimension.width()) + 1;
  }

  private static double clamp(TuningDimension dimension, double value) {
    return Math.clamp(value, dimension.minimum(), dimension.maximum());
  }
}
