package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record OrderHistoryEntry(
    String clientOrderId,
    Optional<Long> exchangeOrderId,
    String broker,
    String ticker,
    OrderSide side,
    String orderType,
    BigDecimal amount,
    OrderAmountType amountType,
    String finalStatus,
    BigDecimal executedQuantity,
    BigDecimal quoteAmount,
    Optional<BigDecimal> averagePrice,
    Optional<Integer> errorCode,
    Optional<String> errorMessage,
    Instant createdAt,
    Instant closedAt,
    List<OrderFill> fills) {

  public OrderHistoryEntry {
    fills = List.copyOf(fills);
  }
}
