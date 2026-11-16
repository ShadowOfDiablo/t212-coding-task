package com.tihomir.t212.tradingbot.account.value;

import java.math.BigDecimal;
import java.time.Instant;

/** The whole account's value in USDT at one moment. */
public record AccountValuePoint(Instant time, BigDecimal value) {}
