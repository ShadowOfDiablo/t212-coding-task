package com.tihomir.t212.tradingbot.position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * A position from its first buy until its last coin is sold. It is identified by the run it opened
 * in and the time of its first buy.
 *
 * @param cost the USDT spent on all its buys, fees included
 * @param mae the worst unrealised P&L while it was open, zero or below
 * @param mfe the best unrealised P&L while it was open, zero or above
 */
public record Position(
    long runId,
    String broker,
    String ticker,
    PositionStatus status,
    Instant openedAt,
    Optional<Instant> closedAt,
    BigDecimal cost,
    BigDecimal realisedPnl,
    BigDecimal mae,
    BigDecimal mfe) {}
