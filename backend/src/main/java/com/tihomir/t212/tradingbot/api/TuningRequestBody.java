package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.TrainingRunRequest;
import com.tihomir.t212.tradingbot.bot.TuningRequest;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A tuning: {@code settings} are the base settings, {@code settingRanges} and {@code timeframes}
 * say what the tuner may change. Tries are scored on {@code [candles.from, checkFrom)}, the best
 * one is recorded on {@code [checkFrom, candles.to)}.
 */
public record TuningRequestBody(
    @NotNull @Valid CandleRangeBody candles,
    @NotNull Instant checkFrom,
    @NotNull @Valid AlgorithmSettingsBody settings,
    @NotNull @Positive BigDecimal startingBalance,
    List<@NotNull @Valid TunedSettingRangeBody> settingRanges,
    List<@NotNull CandleInterval> timeframes,
    @NotNull @Positive @Max(TuningRequestBody.MAX_TRIALS) Integer maxTrials) {

  static final long MAX_TRIALS = 200;

  @AssertTrue(message = "checkFrom must lie after candles.from and before candles.to")
  public boolean isCheckFromInsideTheRange() {
    if (candles == null || candles.from() == null || candles.to() == null || checkFrom == null) {
      return true;
    }
    return checkFrom.isAfter(candles.from()) && checkFrom.isBefore(candles.to());
  }

  TuningRequest toTuningRequest(String broker) {
    return new TuningRequest(
        new TrainingRunRequest(
            broker,
            candles.ticker(),
            candles.from(),
            candles.to(),
            settings.toSettingsRequest(),
            startingBalance),
        checkFrom,
        Optional.ofNullable(settingRanges).orElse(List.of()).stream()
            .map(TunedSettingRangeBody::toTunedSettingRange)
            .toList(),
        Optional.ofNullable(timeframes).orElse(List.of()),
        maxTrials);
  }
}
