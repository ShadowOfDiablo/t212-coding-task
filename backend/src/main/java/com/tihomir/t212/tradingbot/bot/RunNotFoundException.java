package com.tihomir.t212.tradingbot.bot;

public class RunNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RunNotFoundException(long runId) {
    super("No run with id " + runId);
  }
}
