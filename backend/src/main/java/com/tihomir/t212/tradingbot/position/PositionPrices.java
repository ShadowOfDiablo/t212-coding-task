package com.tihomir.t212.tradingbot.position;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * What a position traded: the coins bought over all its buys, their average price in USDT with buy
 * fees included, and the average price of its sells so far (empty before the first sell).
 */
public record PositionPrices(
    BigDecimal quantity, BigDecimal entryPrice, Optional<BigDecimal> exitPrice) {}
