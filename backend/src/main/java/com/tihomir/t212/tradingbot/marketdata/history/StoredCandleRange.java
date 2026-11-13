package com.tihomir.t212.tradingbot.marketdata.history;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.time.Instant;

/**
 * The first and last stored candle of a ticker and interval. Downloads only ever extend this range
 * at its ends, so the candles between them have no holes the exchange could fill.
 */
public record StoredCandleRange(
    CandleInterval interval, Instant firstOpenTime, Instant lastOpenTime) {

  boolean startsAfter(Instant time) {
    return firstOpenTime.isAfter(time);
  }

  boolean endsBefore(Instant time) {
    return endTime().isBefore(time);
  }

  Instant endTime() {
    return lastOpenTime.plus(interval.duration());
  }
}
