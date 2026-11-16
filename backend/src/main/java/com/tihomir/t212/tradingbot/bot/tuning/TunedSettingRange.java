package com.tihomir.t212.tradingbot.bot.tuning;

import java.math.BigDecimal;

/** A setting the tuner may change, between these bounds (both included). */
public record TunedSettingRange(String name, BigDecimal minimum, BigDecimal maximum) {}
