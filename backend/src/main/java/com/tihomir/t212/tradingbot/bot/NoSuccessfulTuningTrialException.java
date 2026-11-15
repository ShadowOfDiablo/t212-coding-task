package com.tihomir.t212.tradingbot.bot;

public class NoSuccessfulTuningTrialException extends RuntimeException {

  public NoSuccessfulTuningTrialException(int trialCount) {
    super("None of the " + trialCount + " tuning tries succeeded, so nothing was recorded");
  }
}
