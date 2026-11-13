package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.position.PositionWriteList;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * How a run ended.
 *
 * @param points the portfolio values of a training run; none for a live run
 * @param positionWrites the position changes of a training run, in order; none for a live run,
 *     which writes them as its orders execute
 */
public record RunEnd(
    RunStatus status,
    Instant finishedAt,
    Optional<BigDecimal> finalValue,
    RunCounts counts,
    Optional<String> failureReason,
    PortfolioPointList points,
    PositionWriteList positionWrites) {}
