package com.tihomir.t212.tradingbot.bot.live.queue;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A live price for one ticker, waiting to be evaluated.
 *
 * @param attempt 1 on the first try, one more for each retry
 */
public record PriceMessage(String ticker, BigDecimal price, Instant time, int attempt) {

  private static final int FIRST_ATTEMPT = 1;

  public static PriceMessage firstAttempt(String ticker, BigDecimal price, Instant time) {
    return new PriceMessage(ticker, price, time, FIRST_ATTEMPT);
  }

  public PriceMessage withNextAttempt() {
    return new PriceMessage(ticker, price, time, attempt + 1);
  }
}
