package com.tihomir.t212.tradingbot.bot;

/** A stored run with its settings records (oldest first) and, for training, its points. */
public record StoredRunDetails(
    RecordedRun run, RunSettingsChangeList settingsChanges, PortfolioPointList points) {}
