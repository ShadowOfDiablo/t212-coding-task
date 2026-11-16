package com.tihomir.t212.tradingbot.algorithm.settings;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings as they were asked for, before {@link TradingAlgorithm#resolveSettings} checks them.
 *
 * @param values the numeric settings by name, shared and the algorithm's own; a value may be {@code
 *     null}, which means "not set"
 */
public record AlgorithmSettingsRequest(
    AlgorithmType algorithmType,
    CandleInterval timeframe,
    boolean liveOnTick,
    Map<String, BigDecimal> values) {

  public AlgorithmSettingsRequest {
    values = Collections.unmodifiableMap(new HashMap<>(values));
  }
}
