package com.tihomir.t212.tradingbot.bot.exception;

public class TickerSettingsMissingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TickerSettingsMissingException(String broker, String ticker) {
    super(ticker + " on " + broker + " has no settings to trade with yet");
  }
}
