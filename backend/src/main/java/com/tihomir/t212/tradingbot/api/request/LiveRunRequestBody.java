package com.tihomir.t212.tradingbot.api.request;

import com.tihomir.t212.tradingbot.bot.model.LiveRunRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LiveRunRequestBody(
    @NotBlank String ticker, @NotNull @Valid AlgorithmSettingsBody settings) {

  public LiveRunRequest toLiveRunRequest(String broker) {
    return new LiveRunRequest(broker, ticker, settings.toSettingsRequest());
  }
}
