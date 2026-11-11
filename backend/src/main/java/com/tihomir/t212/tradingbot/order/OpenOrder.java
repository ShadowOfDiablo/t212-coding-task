package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OpenOrder(
    long exchangeOrderId,
    String clientOrderId,
    String broker,
    String ticker,
    OrderSide side,
    String orderType,
    BigDecimal amount,
    OrderAmountType amountType,
    OpenOrderStatus status,
    Instant createdAt,
    Instant updatedAt) {}
