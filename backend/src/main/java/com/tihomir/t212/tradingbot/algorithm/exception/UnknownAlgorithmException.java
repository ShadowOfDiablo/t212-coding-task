package com.tihomir.t212.tradingbot.algorithm.exception;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;

public class UnknownAlgorithmException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownAlgorithmException(AlgorithmType algorithmType) {
    super("Unknown algorithm: " + algorithmType);
  }
}
