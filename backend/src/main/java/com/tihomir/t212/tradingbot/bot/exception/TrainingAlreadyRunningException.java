package com.tihomir.t212.tradingbot.bot.exception;

public class TrainingAlreadyRunningException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TrainingAlreadyRunningException() {
    super("A training run is already going. Wait for it to finish.");
  }
}
