package com.tihomir.t212.tradingbot.order.exception;

public class TickerNotTradedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TickerNotTradedException(String brokerName, String ticker) {
    super(ticker + " is not one of the tickers configured for " + brokerName);
  }
}
