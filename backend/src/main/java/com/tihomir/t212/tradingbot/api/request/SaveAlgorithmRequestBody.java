package com.tihomir.t212.tradingbot.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveAlgorithmRequestBody(
    @NotBlank @Size(max = SaveAlgorithmRequestBody.MAX_NAME_LENGTH) String name,
    @NotNull @Valid AlgorithmSettingsBody settings) {

  static final int MAX_NAME_LENGTH = 60;
}
