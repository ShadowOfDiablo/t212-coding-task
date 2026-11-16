package com.tihomir.t212.tradingbot.order.exception;

public class TradingNotConfiguredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TradingNotConfiguredException() {
    super("Trading is off: no demo API key is configured");
  }
}
