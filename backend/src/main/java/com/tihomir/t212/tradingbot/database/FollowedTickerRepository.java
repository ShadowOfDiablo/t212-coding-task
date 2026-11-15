package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.marketdata.tickerprice.FollowedTickerStore;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Followed tickers are the rows of {@code ticker} with a {@code followed_at}. */
@Repository
public class FollowedTickerRepository implements FollowedTickerStore {

  private final JdbcClient jdbcClient;
  private final CandleRepository candleRepository;

  public FollowedTickerRepository(JdbcClient jdbcClient, CandleRepository candleRepository) {
    this.jdbcClient = jdbcClient;
    this.candleRepository = candleRepository;
  }

  /** A ticker already followed keeps its first follow time. */
  @Override
  @Transactional
  public void insertFollowedTicker(String broker, String ticker, Instant followedAt) {
    long tickerId = candleRepository.insertTicker(broker, ticker);
    jdbcClient
        .sql("UPDATE ticker SET followed_at = ? WHERE id = ? AND followed_at IS NULL")
        .param(OffsetDateTime.ofInstant(followedAt, ZoneOffset.UTC))
        .param(tickerId)
        .update();
  }

  @Override
  public TickerList getFollowedTickers(String broker) {
    return new TickerList(
        jdbcClient
            .sql(
                """
                SELECT t.symbol FROM ticker t
                JOIN brokers b ON b.broker_id = t.broker_id
                WHERE b.name = ? AND t.followed_at IS NOT NULL
                ORDER BY t.followed_at, t.symbol
                """)
            .param(broker)
            .query(String.class)
            .list());
  }
}
