package com.tihomir.t212.tradingbot.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Optional;

/** Settings picked by hand for one ticker; without a budget the ticker keeps its own. */
public record TickerSettingsRequestBody(
    @NotNull @Valid AlgorithmSettingsBody settings, @Positive BigDecimal budget) {

  public Optional<BigDecimal> getBudget() {
    return Optional.ofNullable(budget);
  }
}
