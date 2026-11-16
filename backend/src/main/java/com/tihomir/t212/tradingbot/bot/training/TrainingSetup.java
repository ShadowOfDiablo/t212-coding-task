package com.tihomir.t212.tradingbot.bot.training;

import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.order.model.TickerTradingRules;

/** Everything a training run needs, checked before anything is recorded. */
public record TrainingSetup(
    TrainingRunRequest request,
    TradingAlgorithm algorithm,
    AlgorithmSettings settings,
    TickerTradingRules tradingRules) {}
