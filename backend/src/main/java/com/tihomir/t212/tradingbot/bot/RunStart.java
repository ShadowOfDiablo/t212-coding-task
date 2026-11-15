package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.mode.BotMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * A run about to start, with the id of the algorithm variant it uses.
 *
 * @param from the start of the replayed range, training only
 * @param to the end of the replayed range, training only
 */
public record RunStart(
    BotMode mode,
    String broker,
    String ticker,
    Instant startedAt,
    Optional<Instant> from,
    Optional<Instant> to,
    BigDecimal startingValue,
    long algorithmId,
    SettingsSource settingsSource) {}
