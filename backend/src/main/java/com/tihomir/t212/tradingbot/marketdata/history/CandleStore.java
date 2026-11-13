package com.tihomir.t212.tradingbot.marketdata.history;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.util.Optional;

public interface CandleStore {

  Optional<Long> getTickerId(String broker, String ticker);

  /** Inserts the ticker unless it is stored already, and returns its id. */
  long insertTicker(String broker, String ticker);

  Optional<StoredCandleRange> getStoredCandleRange(long tickerId, CandleInterval interval);

  /** Inserts the candles of the page that aren't stored yet, all or none. */
  void insertCandlePage(long tickerId, CandleList page);

  /** The stored candles with an open time in {@code [from, to)}, oldest first. */
  CandleList getCandles(CandleHistoryRequest request);
}
