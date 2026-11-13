package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;

public record LiveBotStatus(
    long runId,
    String ticker,
    AlgorithmSettings currentParameters,
    long settingsId,
    RunCounts counts,
    PortfolioPointList points) {}
