package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;

public record LiveRunRequest(String broker, String ticker, AlgorithmSettingsRequest settings) {}
