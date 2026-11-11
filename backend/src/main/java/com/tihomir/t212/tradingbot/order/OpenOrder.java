package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OpenOrder(
    String clientOrderId,
    String broker,
    String ticker,
    OrderSide side,
    String orderType,
    BigDecimal amount,
    OrderAmountType amountType,
    OpenOrderStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public static final String MARKET_ORDER_TYPE = "MARKET";

  static OpenOrder sending(
      String clientOrderId, String broker, MarketOrderRequest request, Instant createdAt) {
    return new OpenOrder(
        clientOrderId,
        broker,
        request.ticker(),
        request.side(),
        MARKET_ORDER_TYPE,
        request.amount(),
        request.amountType(),
        OpenOrderStatus.SENDING,
        createdAt,
        createdAt);
  }
}
