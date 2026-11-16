package com.tihomir.t212.tradingbot.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExecutedOrder(
    long exchangeOrderId,
    String clientOrderId,
    String ticker,
    OrderSide side,
    String exchangeStatus,
    BigDecimal executedQuantity,
    BigDecimal quoteAmount,
    BigDecimal averagePrice,
    List<OrderFill> fills,
    Instant transactTime) {

  public ExecutedOrder {
    fills = List.copyOf(fills);
  }
}
