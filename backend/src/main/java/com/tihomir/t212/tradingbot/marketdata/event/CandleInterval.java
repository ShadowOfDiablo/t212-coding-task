package com.tihomir.t212.tradingbot.marketdata.event;

import java.time.Duration;

public enum CandleInterval {
  ONE_SECOND(Duration.ofSeconds(1)),
  ONE_MINUTE(Duration.ofMinutes(1)),
  FIVE_MINUTES(Duration.ofMinutes(5)),
  FIFTEEN_MINUTES(Duration.ofMinutes(15)),
  ONE_HOUR(Duration.ofHours(1)),
  FOUR_HOURS(Duration.ofHours(4)),
  ONE_DAY(Duration.ofDays(1));

  private final Duration duration;

  CandleInterval(Duration duration) {
    this.duration = duration;
  }

  public Duration duration() {
    return duration;
  }
}
