package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.order.TickerTradingRules;

/** Everything a training run needs, checked before anything is recorded. */
record TrainingSetup(
    TrainingRunRequest request,
    TradingAlgorithm algorithm,
    AlgorithmSettings settings,
    TickerTradingRules tradingRules) {}
