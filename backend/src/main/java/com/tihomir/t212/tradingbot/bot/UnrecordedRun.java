package com.tihomir.t212.tradingbot.bot;

import java.math.BigDecimal;

/** A training replay that was never stored, such as the old settings a tuning compares against. */
public record UnrecordedRun(BigDecimal finalValue, RunCounts counts, PortfolioPointList points) {}
