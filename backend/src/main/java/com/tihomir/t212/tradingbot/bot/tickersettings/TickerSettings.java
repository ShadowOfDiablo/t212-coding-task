package com.tihomir.t212.tradingbot.bot.tickersettings;

import java.util.Optional;

/** A followed ticker and its assigned settings, empty until it has some. */
public record TickerSettings(String ticker, Optional<AssignedSettings> assigned) {}
