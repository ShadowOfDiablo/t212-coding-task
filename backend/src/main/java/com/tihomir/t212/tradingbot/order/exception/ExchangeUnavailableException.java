package com.tihomir.t212.tradingbot.order.exception;

public class ExchangeUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ExchangeUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public ExchangeUnavailableException(String message) {
    super(message);
  }
}
