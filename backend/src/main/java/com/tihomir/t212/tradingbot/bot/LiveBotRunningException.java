package com.tihomir.t212.tradingbot.bot;

public class LiveBotRunningException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LiveBotRunningException(String broker) {
    super("The live bot of " + broker + " is running. Stop it first.");
  }
}
