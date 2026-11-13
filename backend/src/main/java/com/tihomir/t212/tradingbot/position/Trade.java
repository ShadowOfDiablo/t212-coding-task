package com.tihomir.t212.tradingbot.position;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One lot, or part of a lot, that a sell used.
 *
 * @param runId the run the sell happened in
 * @param buyOrderId the exchange order id of the buy that made the lot
 * @param sellOrderId the exchange order id of the sell; shared by every trade of one sell
 * @param fee this trade's share of the sell fee, in USDT
 * @param realisedPnl (exit price - entry price) x quantity - fee
 */
public record Trade(
    long runId,
    long buyOrderId,
    long sellOrderId,
    Instant boughtAt,
    Instant soldAt,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal fee,
    BigDecimal realisedPnl) {}
