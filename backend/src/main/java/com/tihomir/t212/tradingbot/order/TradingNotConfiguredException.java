package com.tihomir.t212.tradingbot.order;

public class TradingNotConfiguredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TradingNotConfiguredException(String brokerName) {
    super("Trading is not configured for " + brokerName + ": no API key is set");
  }
}
