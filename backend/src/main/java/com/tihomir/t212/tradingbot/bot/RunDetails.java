package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;

public record RunDetails(RecordedRun run, AlgorithmSettings settings, PortfolioPointList points) {}
