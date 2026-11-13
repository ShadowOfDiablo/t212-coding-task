package com.tihomir.t212.tradingbot.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** The ticker and period a request body asks for. The broker comes from the URL. */
public record CandleRangeBody(@NotBlank String ticker, @NotNull Instant from, @NotNull Instant to) {

  @AssertTrue(message = "from must be before to")
  public boolean isFromBeforeTo() {
    return from == null || to == null || from.isBefore(to);
  }
}
