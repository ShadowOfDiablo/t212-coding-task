package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import java.time.Instant;

/** One settings record of a run with the settings it points at. */
public record RunSettingsRecord(
    RunEvent event, Instant recordedAt, long settingsId, AlgorithmSettings settings) {}
