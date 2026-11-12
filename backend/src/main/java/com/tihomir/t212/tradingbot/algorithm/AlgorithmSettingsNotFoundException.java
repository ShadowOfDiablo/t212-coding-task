package com.tihomir.t212.tradingbot.algorithm;

public class AlgorithmSettingsNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AlgorithmSettingsNotFoundException(long settingsId) {
    super("No algorithm settings with id " + settingsId);
  }
}
