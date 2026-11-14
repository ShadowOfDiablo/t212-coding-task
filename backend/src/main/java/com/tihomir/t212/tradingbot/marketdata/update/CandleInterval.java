package com.tihomir.t212.tradingbot.marketdata.update;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/** A candle length. The API writes it as a short code such as "5m", the database in seconds. */
public enum CandleInterval {
  ONE_SECOND(Duration.ofSeconds(1)),
  ONE_MINUTE(Duration.ofMinutes(1)),
  FIVE_MINUTES(Duration.ofMinutes(5)),
  FIFTEEN_MINUTES(Duration.ofMinutes(15)),
  ONE_HOUR(Duration.ofHours(1)),
  FOUR_HOURS(Duration.ofHours(4)),
  ONE_DAY(Duration.ofDays(1));

  private static final long SECONDS_PER_MINUTE = 60;
  private static final long MINUTES_PER_HOUR = 60;
  private static final long HOURS_PER_DAY = 24;
  private static final Duration CLOSE_TIME_OFFSET = Duration.ofMillis(1);

  private final Duration duration;

  CandleInterval(Duration duration) {
    this.duration = duration;
  }

  public Duration duration() {
    return duration;
  }

  /** The length in its largest whole unit: "1s", "5m", "4h", "1d". */
  @JsonValue
  public String code() {
    if (duration.toSeconds() < SECONDS_PER_MINUTE) {
      return duration.toSeconds() + "s";
    }
    if (duration.toMinutes() < MINUTES_PER_HOUR) {
      return duration.toMinutes() + "m";
    }
    if (duration.toHours() < HOURS_PER_DAY) {
      return duration.toHours() + "h";
    }
    return duration.toDays() + "d";
  }

  /**
   * @throws IllegalArgumentException if no interval has this code
   */
  @JsonCreator
  public static CandleInterval fromCode(String code) {
    return Arrays.stream(values())
        .filter(interval -> interval.code().equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown candle interval: " + code));
  }

  /**
   * @throws IllegalArgumentException if no interval is this many seconds long
   */
  public static CandleInterval fromSeconds(long seconds) {
    return Arrays.stream(values())
        .filter(interval -> interval.duration.toSeconds() == seconds)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown candle interval: " + seconds + "s"));
  }

  /** A candle closes at its last millisecond: open time + length - 1 ms. */
  public Instant closeTime(Instant openTime) {
    return openTime.plus(duration).minus(CLOSE_TIME_OFFSET);
  }
}
