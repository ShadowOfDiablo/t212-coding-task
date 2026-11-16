package com.tihomir.t212.tradingbot.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ReplaceSavedSettingsRequestBody(@NotNull @Valid AlgorithmSettingsBody settings) {}
