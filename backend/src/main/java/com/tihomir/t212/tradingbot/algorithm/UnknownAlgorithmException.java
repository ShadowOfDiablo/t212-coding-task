package com.tihomir.t212.tradingbot.algorithm;

public class UnknownAlgorithmException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownAlgorithmException(AlgorithmType algorithmType) {
    super("Unknown algorithm: " + algorithmType);
  }
}
