package com.tihomir.t212.tradingbot.position;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One lot, or part of a lot, that a sell used.
 *
 * @param runId the run the sell happened in
 * @param fee this trade's share of the sell fee, in USDT
 * @param realisedPnl (exit price - entry price) x quantity - fee
 */
public record Trade(
    long runId,
    Instant boughtAt,
    Instant soldAt,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal fee,
    BigDecimal realisedPnl) {}
