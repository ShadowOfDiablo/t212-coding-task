package com.tihomir.t212.tradingbot.bot.run;

import java.math.BigDecimal;
import java.time.Instant;

/** A candle's closing price at its open time. */
public record PricePoint(Instant time, BigDecimal price) {}
