package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The settings of an algorithm in a request body. Settings left out ({@code null}) take their
 * default when required and are skipped when optional. {@code options} holds the algorithm's own
 * settings by name; the algorithm checks those.
 */
public record AlgorithmSettingsBody(
    @NotNull Integer algorithmId,
    @NotNull CandleInterval timeframe,
    @NotNull Boolean liveOnTick,
    @Positive Integer timePeriod,
    @Positive Integer updateTimeSeconds,
    @Positive @DecimalMax("100") BigDecimal orderSizePercent,
    @Positive @DecimalMax("100") BigDecimal riskPerTradePercent,
    @Positive @DecimalMax(value = "100", inclusive = false) BigDecimal stopLossPercent,
    Map<String, BigDecimal> options) {

  @AssertTrue(message = "updateTimeSeconds is set exactly when liveOnTick is off")
  public boolean isUpdateTimeSetWhenNotOnTick() {
    return liveOnTick == null || liveOnTick == (updateTimeSeconds == null);
  }

  @AssertTrue(message = "riskPerTradePercent and stopLossPercent are set together")
  public boolean isRiskComplete() {
    return (riskPerTradePercent == null) == (stopLossPercent == null);
  }

  AlgorithmSettingsRequest toSettingsRequest() {
    Map<String, BigDecimal> values = new HashMap<>();
    Optional.ofNullable(options).ifPresent(values::putAll);
    putIfSet(
        values,
        TradingAlgorithm.TIME_PERIOD,
        Optional.ofNullable(timePeriod).map(BigDecimal::valueOf));
    putIfSet(
        values,
        TradingAlgorithm.UPDATE_TIME_SECONDS,
        Optional.ofNullable(updateTimeSeconds).map(BigDecimal::valueOf));
    putIfSet(values, TradingAlgorithm.ORDER_SIZE_PERCENT, Optional.ofNullable(orderSizePercent));
    putIfSet(
        values, TradingAlgorithm.RISK_PER_TRADE_PERCENT, Optional.ofNullable(riskPerTradePercent));
    putIfSet(values, TradingAlgorithm.STOP_LOSS_PERCENT, Optional.ofNullable(stopLossPercent));
    return new AlgorithmSettingsRequest(algorithmId, timeframe, liveOnTick, values);
  }

  /** A setting left out stays out of the map, so the algorithm uses its default or skips it. */
  private static void putIfSet(
      Map<String, BigDecimal> values, String name, Optional<BigDecimal> value) {
    value.ifPresent(setValue -> values.put(name, setValue));
  }
}
