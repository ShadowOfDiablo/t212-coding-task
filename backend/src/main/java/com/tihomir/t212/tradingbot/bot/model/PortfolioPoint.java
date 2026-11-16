package com.tihomir.t212.tradingbot.bot.model;

import java.math.BigDecimal;
import java.time.Instant;

/** The account at one moment of a training run: cash, and cash plus the position at the close. */
public record PortfolioPoint(Instant time, BigDecimal cash, BigDecimal value) {}
