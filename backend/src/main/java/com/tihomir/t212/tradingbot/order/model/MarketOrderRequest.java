package com.tihomir.t212.tradingbot.order.model;

import java.math.BigDecimal;

public record MarketOrderRequest(
    String ticker, OrderSide side, BigDecimal amount, OrderAmountType amountType) {

  public static final String ORDER_TYPE = "MARKET";

  public MarketOrderRequest {
    if (ticker == null || ticker.isBlank()) {
      throw new IllegalArgumentException("ticker is required");
    }
    if (side == null) {
      throw new IllegalArgumentException("side is required");
    }
    if (amountType == null) {
      throw new IllegalArgumentException("amountType is required");
    }
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
  }
}
