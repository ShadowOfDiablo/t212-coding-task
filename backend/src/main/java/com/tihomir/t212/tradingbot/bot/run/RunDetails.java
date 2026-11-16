package com.tihomir.t212.tradingbot.bot.run;

import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;

public record RunDetails(RecordedRun run, AlgorithmSettings settings, PortfolioPointList points) {}
