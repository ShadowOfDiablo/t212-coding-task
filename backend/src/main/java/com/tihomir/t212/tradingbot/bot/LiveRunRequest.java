package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;

/** A live bot to start, built from a request body already checked with {@code @Valid}. */
public record LiveRunRequest(String broker, String ticker, AlgorithmSettingsRequest settings) {}
