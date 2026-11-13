package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryRequest;
import com.tihomir.t212.tradingbot.marketdata.history.CandleStore;
import com.tihomir.t212.tradingbot.marketdata.history.StoredCandleRange;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CandleRepository implements CandleStore {

  private static final String INSERT_MISSING_CANDLE =
      """
      INSERT INTO candle (ticker_id, candle_interval, open_time, close_time, open, high, low, close,
                          volume)
      SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?
      WHERE NOT EXISTS (
          SELECT 1 FROM candle WHERE ticker_id = ? AND candle_interval = ? AND open_time = ?)
      """;

  private final JdbcClient jdbcClient;
  private final JdbcTemplate jdbcTemplate;

  public CandleRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
    this.jdbcClient = jdbcClient;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<Long> getTickerId(String broker, String ticker) {
    return jdbcClient
        .sql("SELECT id FROM ticker WHERE broker = ? AND symbol = ?")
        .param(broker)
        .param(ticker)
        .query(Long.class)
        .optional();
  }

  @Override
  public long insertTicker(String broker, String ticker) {
    jdbcClient
        .sql(
            """
            INSERT INTO ticker (broker, symbol)
            SELECT ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM ticker WHERE broker = ? AND symbol = ?)
            """)
        .param(broker)
        .param(ticker)
        .param(broker)
        .param(ticker)
        .update();
    return getTickerId(broker, ticker)
        .orElseThrow(() -> new IllegalStateException("No id stored for ticker " + ticker));
  }

  @Override
  public Optional<StoredCandleRange> getStoredCandleRange(long tickerId, CandleInterval interval) {
    return jdbcClient
        .sql(
            """
            SELECT MIN(open_time) AS first_open_time, MAX(open_time) AS last_open_time
            FROM candle
            WHERE ticker_id = ? AND candle_interval = ?
            HAVING COUNT(*) > 0
            """)
        .param(tickerId)
        .param(interval.code())
        .query((row, rowNumber) -> storedCandleRange(row, interval))
        .optional();
  }

  @Override
  @Transactional
  public void insertCandlePage(long tickerId, CandleList page) {
    List<Object[]> rows =
        page.candles().stream().map(candle -> candleRow(tickerId, candle)).toList();
    jdbcTemplate.batchUpdate(INSERT_MISSING_CANDLE, rows);
  }

  @Override
  public CandleList getCandles(CandleHistoryRequest request) {
    return new CandleList(
        jdbcClient
            .sql(
                """
                SELECT t.symbol, c.*
                FROM candle c
                JOIN ticker t ON t.id = c.ticker_id
                WHERE t.broker = ? AND t.symbol = ? AND c.candle_interval = ?
                  AND c.open_time >= ? AND c.open_time < ?
                ORDER BY c.open_time
                """)
            .param(request.broker())
            .param(request.ticker())
            .param(request.interval().code())
            .param(timestamp(request.from()))
            .param(timestamp(request.to()))
            .query(CandleRepository::candle)
            .list());
  }

  private static Object[] candleRow(long tickerId, CandleUpdate candle) {
    String interval = candle.interval().code();
    OffsetDateTime openTime = timestamp(candle.openTime());
    return new Object[] {
      tickerId,
      interval,
      openTime,
      timestamp(candle.closeTime()),
      candle.open(),
      candle.high(),
      candle.low(),
      candle.close(),
      candle.volume(),
      tickerId,
      interval,
      openTime
    };
  }

  private static StoredCandleRange storedCandleRange(ResultSet row, CandleInterval interval)
      throws SQLException {
    return new StoredCandleRange(
        interval, instant(row, "first_open_time"), instant(row, "last_open_time"));
  }

  private static CandleUpdate candle(ResultSet row, int rowNumber) throws SQLException {
    Instant closeTime = instant(row, "close_time");
    return new CandleUpdate(
        row.getString("symbol"),
        CandleInterval.fromCode(row.getString("candle_interval")),
        instant(row, "open_time"),
        closeTime,
        row.getBigDecimal("open"),
        row.getBigDecimal("high"),
        row.getBigDecimal("low"),
        row.getBigDecimal("close"),
        row.getBigDecimal("volume"),
        true,
        closeTime);
  }

  private static OffsetDateTime timestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    return row.getObject(column, OffsetDateTime.class).toInstant();
  }
}
