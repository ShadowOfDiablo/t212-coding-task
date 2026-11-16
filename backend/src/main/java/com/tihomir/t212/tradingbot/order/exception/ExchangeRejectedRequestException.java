package com.tihomir.t212.tradingbot.order.exception;

public class ExchangeRejectedRequestException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int exchangeErrorCode;

  public ExchangeRejectedRequestException(int exchangeErrorCode, String exchangeMessage) {
    super(exchangeMessage);
    this.exchangeErrorCode = exchangeErrorCode;
  }

  public int exchangeErrorCode() {
    return exchangeErrorCode;
  }
}
