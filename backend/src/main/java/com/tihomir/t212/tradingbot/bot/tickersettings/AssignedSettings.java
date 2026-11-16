package com.tihomir.t212.tradingbot.bot.tickersettings;

import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * The settings a ticker trades with.
 *
 * @param budget the ticker's own money in USDT; empty on rows written before budgets existed
 * @param tunedRunId the run of the ticker's last successful auto-tune
 */
public record AssignedSettings(
    long algorithmId,
    AlgorithmSettings settings,
    Optional<BigDecimal> budget,
    SettingsSource source,
    Optional<Long> tunedRunId) {}
