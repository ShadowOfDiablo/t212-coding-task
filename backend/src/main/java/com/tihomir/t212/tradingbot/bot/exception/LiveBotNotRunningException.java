package com.tihomir.t212.tradingbot.bot.exception;

public class LiveBotNotRunningException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LiveBotNotRunningException(String broker) {
    super("The live bot of " + broker + " is not running");
  }
}
