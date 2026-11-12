package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A training run to start, built from a request body already checked with {@code @Valid}. The
 * candles of the settings' timeframe in {@code [from, to)} are replayed.
 */
public record TrainingRunRequest(
    String broker,
    String ticker,
    Instant from,
    Instant to,
    AlgorithmSettingsRequest settings,
    BigDecimal startingBalance) {}
