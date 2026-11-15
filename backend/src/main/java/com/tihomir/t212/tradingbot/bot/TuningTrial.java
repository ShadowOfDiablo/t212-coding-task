package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;

/**
 * One try of a tuning, kept in memory only.
 *
 * @param score the return of the replay as a fraction (0.05 is 5%), or {@link #FAILED_TRIAL_SCORE}
 *     when the try failed
 */
record TuningTrial(TuningPoint point, AlgorithmSettingsRequest settings, double score) {

  static final double FAILED_TRIAL_SCORE = Double.NEGATIVE_INFINITY;

  boolean isSuccessful() {
    return score != FAILED_TRIAL_SCORE;
  }
}
