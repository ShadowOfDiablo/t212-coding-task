package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;

/** A running live bot: its run, the settings in force and what it did so far. */
public record LiveBotStatus(
    long runId,
    String ticker,
    AlgorithmSettings currentParameters,
    long settingsId,
    RunCounts counts) {}
