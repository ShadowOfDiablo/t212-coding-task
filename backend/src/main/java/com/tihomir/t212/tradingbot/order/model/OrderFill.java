package com.tihomir.t212.tradingbot.order.model;

import java.math.BigDecimal;

public record OrderFill(
    long tradeId,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal feeAmount,
    String feeCurrency) {}
