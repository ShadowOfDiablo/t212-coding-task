package com.tihomir.t212.tradingbot.marketdata.history;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.time.Instant;

/**
 * A range of candles to read. Built from a request body that was already checked with
 * {@code @Valid}.
 */
public record CandleHistoryRequest(
    String broker, String ticker, CandleInterval interval, Instant from, Instant to) {

  CandleHistoryRequest endingAt(Instant newTo) {
    return new CandleHistoryRequest(broker, ticker, interval, from, newTo);
  }
}
