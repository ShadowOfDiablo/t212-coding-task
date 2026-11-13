package com.tihomir.t212.tradingbot.algorithm;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Checked settings of one run: the ones every algorithm shares, plus the algorithm's own optional
 * values. An empty optional skips every check that uses it.
 */
public record AlgorithmSettings(
    AlgorithmType algorithmType,
    int timePeriod,
    CandleInterval timeframe,
    boolean liveOnTick,
    Optional<Integer> updateTimeSeconds,
    BigDecimal orderSizePercent,
    Optional<BigDecimal> riskPerTradePercent,
    Optional<BigDecimal> stopLossPercent,
    AlgorithmSettingValues options) {}
