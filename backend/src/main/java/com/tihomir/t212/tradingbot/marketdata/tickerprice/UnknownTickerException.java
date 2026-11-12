package com.tihomir.t212.tradingbot.marketdata.tickerprice;

public class UnknownTickerException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownTickerException(String broker, String ticker, Throwable cause) {
    super(broker + " does not list the ticker " + ticker, cause);
  }
}
