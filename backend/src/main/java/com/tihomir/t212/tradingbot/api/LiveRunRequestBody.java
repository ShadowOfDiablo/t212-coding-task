package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.LiveRunRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LiveRunRequestBody(
    @NotBlank String ticker, @NotNull @Valid AlgorithmSettingsBody settings) {

  LiveRunRequest toLiveRunRequest(String broker) {
    return new LiveRunRequest(broker, ticker, settings.toSettingsRequest());
  }
}
