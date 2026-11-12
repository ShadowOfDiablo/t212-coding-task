package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataAccessResourceFailureException;

final class FakeFollowedTickerStore implements FollowedTickerStore {

  private final List<String> tickers = new ArrayList<>();
  private boolean databaseDown;

  @Override
  public void insertFollowedTicker(String broker, String ticker, Instant followedAt) {
    failIfDatabaseDown();
    if (!tickers.contains(ticker)) {
      tickers.add(ticker);
    }
  }

  @Override
  public TickerList getFollowedTickers(String broker) {
    failIfDatabaseDown();
    return new TickerList(tickers);
  }

  void takeDatabaseDown() {
    databaseDown = true;
  }

  private void failIfDatabaseDown() {
    if (databaseDown) {
      throw new DataAccessResourceFailureException("connection refused");
    }
  }
}
