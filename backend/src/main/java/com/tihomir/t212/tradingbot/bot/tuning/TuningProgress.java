package com.tihomir.t212.tradingbot.bot.tuning;

record TuningProgress(int trialsDone, int maxTrials) {

  private static final int PERCENT = 100;

  /** Whole percent of the tries done, rounded down. */
  int percentDone() {
    return trialsDone * PERCENT / maxTrials;
  }
}
