package com.tihomir.t212.tradingbot.algorithm;

public class UnknownAlgorithmException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownAlgorithmException(String algorithmId) {
    super("Unknown algorithm: " + algorithmId);
  }
}
