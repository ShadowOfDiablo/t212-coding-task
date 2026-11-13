package com.tihomir.t212.tradingbot.bot;

import java.time.Instant;

/** One settings record of a run, pointing at the stored settings. */
public record RunSettingsChange(RunEvent event, Instant recordedAt, long settingsId) {}
