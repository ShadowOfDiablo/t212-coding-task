package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.marketdata.tickerprice.FollowedTickerStore;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FollowedTickerRepository implements FollowedTickerStore {

  private final JdbcClient jdbcClient;

  public FollowedTickerRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void insertFollowedTicker(String broker, String ticker, Instant followedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO followed_ticker (broker, symbol, followed_at)
            SELECT ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM followed_ticker WHERE broker = ? AND symbol = ?)
            """)
        .param(broker)
        .param(ticker)
        .param(OffsetDateTime.ofInstant(followedAt, ZoneOffset.UTC))
        .param(broker)
        .param(ticker)
        .update();
  }

  @Override
  public TickerList getFollowedTickers(String broker) {
    return new TickerList(
        jdbcClient
            .sql("SELECT symbol FROM followed_ticker WHERE broker = ? ORDER BY followed_at, symbol")
            .param(broker)
            .query(String.class)
            .list());
  }
}
