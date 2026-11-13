package com.tihomir.t212.tradingbot.position;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A lot: one buy that still holds coins. A position is the sum of its lots, and a sell uses up the
 * oldest lot first (FIFO). Not the same as the exchange's lot step, the smallest amount a coin
 * quantity can change by.
 *
 * @param runId the run the buy happened in
 * @param buyOrderId the exchange order id of the buy
 * @param entryPrice the USDT spent per coin received, so the buy fee is part of it
 */
public record OpenLot(
    long runId, long buyOrderId, Instant boughtAt, BigDecimal quantity, BigDecimal entryPrice) {}
