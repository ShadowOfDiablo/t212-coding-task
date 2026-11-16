package com.tihomir.t212.tradingbot.algorithm;

public class SavedAlgorithmNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SavedAlgorithmNotFoundException(long savedAlgorithmId) {
    super("No saved settings with id " + savedAlgorithmId);
  }
}
