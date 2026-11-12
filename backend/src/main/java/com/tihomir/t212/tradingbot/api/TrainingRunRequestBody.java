package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.TrainingRunRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TrainingRunRequestBody(
    @NotNull @Valid CandleRangeBody candles,
    @NotNull @Valid AlgorithmSettingsBody settings,
    @NotNull @Positive BigDecimal startingBalance) {

  TrainingRunRequest toTrainingRunRequest(String broker) {
    return new TrainingRunRequest(
        broker,
        candles.ticker(),
        candles.from(),
        candles.to(),
        settings.toSettingsRequest(),
        startingBalance);
  }
}
