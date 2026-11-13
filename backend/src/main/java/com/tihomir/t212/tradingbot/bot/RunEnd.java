package com.tihomir.t212.tradingbot.bot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * How a run ended.
 *
 * @param points the portfolio values of a training run; none for a live run
 */
public record RunEnd(
    RunStatus status,
    Instant finishedAt,
    Optional<BigDecimal> finalValue,
    RunCounts counts,
    Optional<String> failureReason,
    PortfolioPointList points) {}
