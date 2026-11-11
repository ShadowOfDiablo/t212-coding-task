package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderHistoryEntry(
    String clientOrderId,
    long exchangeOrderId,
    String broker,
    String ticker,
    OrderSide side,
    String orderType,
    BigDecimal amount,
    OrderAmountType amountType,
    String finalStatus,
    BigDecimal executedQuantity,
    BigDecimal quoteAmount,
    BigDecimal averagePrice,
    Instant createdAt,
    Instant closedAt,
    List<OrderFill> fills) {

  public OrderHistoryEntry {
    fills = List.copyOf(fills);
  }
}
