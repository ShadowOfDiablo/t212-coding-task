package com.tihomir.t212.tradingbot.bot.model;

import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;

public record LiveRunRequest(String broker, String ticker, AlgorithmSettingsRequest settings) {}
