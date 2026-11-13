package com.tihomir.t212.tradingbot.position;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A buy that still holds coins.
 *
 * @param runId the run the buy happened in
 * @param buyOrderId the exchange order id of the buy
 * @param entryPrice the USDT spent per coin received, so the buy fee is part of it
 */
public record OpenLot(
    long runId, long buyOrderId, Instant boughtAt, BigDecimal quantity, BigDecimal entryPrice) {}
