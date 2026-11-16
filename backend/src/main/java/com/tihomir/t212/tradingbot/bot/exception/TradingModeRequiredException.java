package com.tihomir.t212.tradingbot.bot.exception;

public class TradingModeRequiredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TradingModeRequiredException() {
    super("The live bot runs only in TRADING mode. Switch the bot mode first.");
  }
}
