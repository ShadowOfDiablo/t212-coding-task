package com.tihomir.t212.tradingbot.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ReplaceSavedSettingsRequestBody(@NotNull @Valid AlgorithmSettingsBody settings) {}
