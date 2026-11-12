package com.tihomir.t212.tradingbot.algorithm;

public class InvalidAlgorithmSettingsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidAlgorithmSettingsException(String message) {
    super(message);
  }
}
