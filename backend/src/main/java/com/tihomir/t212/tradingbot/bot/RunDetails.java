package com.tihomir.t212.tradingbot.bot;

/** A run with the settings it used over time and, for training, its portfolio values. */
public record RunDetails(
    RecordedRun run, RunSettingsHistory settingsHistory, PortfolioPointList points) {}
