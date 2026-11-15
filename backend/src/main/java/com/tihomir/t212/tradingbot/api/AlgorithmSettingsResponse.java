package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Settings as the API shows them; empty optional settings are JSON {@code null}. */
public record AlgorithmSettingsResponse(
    AlgorithmType algorithmType,
    int timePeriod,
    CandleInterval timeframe,
    boolean liveOnTick,
    Optional<Integer> updateTimeSeconds,
    BigDecimal orderSizePercent,
    Optional<BigDecimal> stopLossPercent,
    Optional<BigDecimal> takeProfitPercent,
    Map<String, BigDecimal> options) {

  static AlgorithmSettingsResponse of(AlgorithmSettings settings) {
    return new AlgorithmSettingsResponse(
        settings.algorithmType(),
        settings.timePeriod(),
        settings.timeframe(),
        settings.liveOnTick(),
        settings.updateTimeSeconds(),
        settings.orderSizePercent(),
        settings.stopLossPercent(),
        settings.takeProfitPercent(),
        settings.options().values());
  }
}
