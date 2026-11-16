package com.tihomir.t212.tradingbot.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

/** {@code DOGE/USDT}, {@code doge/usdt} and {@code DOGEUSDT} name the same ticker. */
public record FollowTickerRequestBody(
    @NotBlank
        @Pattern(
            regexp = "(?=(?:[A-Za-z0-9]/?){2,20}$)[A-Za-z0-9]+(/[A-Za-z0-9]+)?",
            message =
                "must be 2 to 20 letters or digits, with an optional / between base and quote")
        String ticker) {

  private static final String BASE_QUOTE_SEPARATOR = "/";

  public String getNormalizedTicker() {
    return ticker.replace(BASE_QUOTE_SEPARATOR, "").toUpperCase(Locale.ROOT);
  }
}
