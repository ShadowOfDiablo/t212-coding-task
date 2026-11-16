package com.tihomir.t212.tradingbot.order.exception;

public class OrderStatusUnknownException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String clientOrderId;

  public OrderStatusUnknownException(String clientOrderId, String reason, Throwable cause) {
    super(reason, cause);
    this.clientOrderId = clientOrderId;
  }

  public OrderStatusUnknownException(String clientOrderId, String reason) {
    super(reason);
    this.clientOrderId = clientOrderId;
  }

  public String clientOrderId() {
    return clientOrderId;
  }
}
