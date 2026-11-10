package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;

public record OrderFill(
    long tradeId,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal commission,
    String commissionAsset) {}
