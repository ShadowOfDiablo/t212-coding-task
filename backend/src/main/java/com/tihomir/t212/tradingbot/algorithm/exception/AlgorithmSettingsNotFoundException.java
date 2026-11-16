package com.tihomir.t212.tradingbot.algorithm.exception;

public class AlgorithmSettingsNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AlgorithmSettingsNotFoundException(long algorithmId) {
    super("No algorithm with id " + algorithmId);
  }
}
