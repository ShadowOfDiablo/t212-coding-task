package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;

public record RejectedOrder(
    String clientOrderId,
    String broker,
    String ticker,
    OrderSide side,
    String orderType,
    BigDecimal amount,
    OrderAmountType amountType,
    int errorCode,
    String errorMessage,
    Instant createdAt,
    Instant rejectedAt) {}
