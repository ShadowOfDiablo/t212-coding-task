package com.tihomir.t212.tradingbot.bot;

/** A stored run with, for training, its points. */
public record StoredRunDetails(RecordedRun run, PortfolioPointList points) {}
