package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record OrderHistoryEntry(
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
    Optional<OrderFee> fee) {

  static OrderHistoryEntry of(
      String broker, MarketOrderRequest request, ExecutedOrder order, Instant createdAt) {
    return new OrderHistoryEntry(
        order.exchangeOrderId(),
        broker,
        order.ticker(),
        order.side(),
        MarketOrderRequest.ORDER_TYPE,
        request.amount(),
        request.amountType(),
        order.exchangeStatus(),
        order.executedQuantity(),
        order.quoteAmount(),
        order.averagePrice(),
        createdAt,
        order.transactTime(),
        OrderFee.of(order.fills()));
  }
}
