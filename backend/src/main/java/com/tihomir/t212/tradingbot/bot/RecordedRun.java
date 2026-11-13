package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.mode.BotMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** A stored run. Optional values are empty while the run goes on, or don't apply to its mode. */
public record RecordedRun(
    long id,
    BotMode mode,
    String broker,
    String ticker,
    long algorithmId,
    RunStatus status,
    Instant startedAt,
    Optional<Instant> finishedAt,
    Optional<Instant> from,
    Optional<Instant> to,
    BigDecimal startingValue,
    Optional<BigDecimal> finalValue,
    RunCounts counts,
    Optional<String> failureReason) {}
