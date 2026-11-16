package com.tihomir.t212.tradingbot.bot.model;

import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.bot.run.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.run.RunCounts;

public record LiveBotStatus(
    long runId,
    String ticker,
    AlgorithmSettings currentParameters,
    long algorithmId,
    RunCounts counts,
    PortfolioPointList points) {}
