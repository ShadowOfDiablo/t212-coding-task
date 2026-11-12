package com.tihomir.t212.tradingbot.marketdata.history;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessResourceFailureException;

public final class FakeCandleStore implements CandleStore {

  private final Map<String, Long> tickerIds = new HashMap<>();
  private final Map<String, CandleUpdate> candles = new HashMap<>();
  private int insertedPages;
  private boolean databaseDown;

  @Override
  public Optional<Long> getTickerId(String broker, String ticker) {
    failIfDatabaseDown();
    return Optional.ofNullable(tickerIds.get(broker + "/" + ticker));
  }

  @Override
  public long insertTicker(String broker, String ticker) {
    failIfDatabaseDown();
    long tickerId = tickerIds.size() + 1L;
    tickerIds.put(broker + "/" + ticker, tickerId);
    return tickerId;
  }

  @Override
  public Optional<StoredCandleRange> getStoredCandleRange(long tickerId, CandleInterval interval) {
    failIfDatabaseDown();
    List<Instant> openTimes =
        candles.values().stream()
            .filter(candle -> candle.interval() == interval)
            .map(CandleUpdate::openTime)
            .sorted()
            .toList();
    if (openTimes.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new StoredCandleRange(interval, openTimes.getFirst(), openTimes.getLast()));
  }

  @Override
  public void insertCandlePage(long tickerId, CandleList page) {
    failIfDatabaseDown();
    page.candles().forEach(candle -> candles.putIfAbsent(candleKey(candle), candle));
    insertedPages++;
  }

  @Override
  public CandleList getCandles(CandleHistoryRequest request) {
    failIfDatabaseDown();
    return new CandleList(
        candles.values().stream()
            .filter(candle -> candle.ticker().equals(request.ticker()))
            .filter(candle -> candle.interval() == request.interval())
            .filter(candle -> isWithin(candle.openTime(), request))
            .sorted(Comparator.comparing(CandleUpdate::openTime))
            .toList());
  }

  public void storeCandles(long tickerId, List<CandleUpdate> storedCandles) {
    tickerIds.put("binance/" + storedCandles.getFirst().ticker(), tickerId);
    insertCandlePage(tickerId, new CandleList(storedCandles));
    insertedPages = 0;
  }

  Optional<StoredCandleRange> storedRange(CandleInterval interval) {
    return getStoredCandleRange(tickerIds.size(), interval);
  }

  public int insertedPages() {
    return insertedPages;
  }

  public void takeDatabaseDown() {
    databaseDown = true;
  }

  private static boolean isWithin(Instant openTime, CandleHistoryRequest request) {
    return !openTime.isBefore(request.from()) && openTime.isBefore(request.to());
  }

  private static String candleKey(CandleUpdate candle) {
    return candle.ticker() + "/" + candle.interval() + "/" + candle.openTime();
  }

  private void failIfDatabaseDown() {
    if (databaseDown) {
      throw new DataAccessResourceFailureException("connection refused");
    }
  }
}
