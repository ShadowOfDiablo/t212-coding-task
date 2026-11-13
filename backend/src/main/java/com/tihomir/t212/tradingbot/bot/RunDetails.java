package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;

/** A run with the settings of its algorithm variant and, for training, its portfolio values. */
public record RunDetails(RecordedRun run, AlgorithmSettings settings, PortfolioPointList points) {}
