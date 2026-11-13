package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.position.OpenLot;
import com.tihomir.t212.tradingbot.position.OpenLotList;
import com.tihomir.t212.tradingbot.position.OpenPosition;
import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.PositionStore;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import com.tihomir.t212.tradingbot.position.StoredPosition;
import com.tihomir.t212.tradingbot.position.StoredPositionList;
import com.tihomir.t212.tradingbot.position.Trade;
import com.tihomir.t212.tradingbot.position.TradeList;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PositionRepository implements PositionStore {

  private final JdbcClient jdbcClient;
  private final JdbcTemplate jdbcTemplate;

  public PositionRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
    this.jdbcClient = jdbcClient;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public void savePosition(PositionWrite write) {
    Position position = write.position();
    if (updatePosition(position) == 0) {
      insertPosition(position);
    }
    long positionId =
        jdbcClient
            .sql("SELECT id FROM position WHERE run_id = ? AND opened_at = ?")
            .param(position.runId())
            .param(timestamp(position.openedAt()))
            .query(Long.class)
            .single();
    jdbcClient.sql("DELETE FROM open_lot WHERE position_id = ?").param(positionId).update();
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO open_lot (position_id, run_id, bought_at, quantity, entry_price)
        VALUES (?, ?, ?, ?, ?)
        """,
        write.openLots().lots().stream().map(lot -> lotRow(positionId, lot)).toList());
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO trade (position_id, run_id, bought_at, sold_at, quantity, entry_price,
                           exit_price, fee, realised_pnl)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        write.newTrades().trades().stream().map(trade -> tradeRow(positionId, trade)).toList());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OpenPosition> findOpenLivePosition(String broker, String ticker) {
    return jdbcClient
        .sql(
            """
            SELECT p.* FROM position p
            JOIN algorithm_run r ON r.id = p.run_id
            WHERE p.broker = ? AND p.ticker = ? AND p.status = ? AND r.mode = ?
            ORDER BY p.opened_at DESC
            LIMIT 1
            """)
        .param(broker)
        .param(ticker)
        .param(PositionStatus.OPEN.name())
        .param(BotMode.TRADING.name())
        .query(PositionRepository::storedPosition)
        .optional()
        .map(this::withOpenLots);
  }

  @Override
  public TradeList getTrades(long runId) {
    return new TradeList(
        jdbcClient
            .sql("SELECT * FROM trade WHERE run_id = ? ORDER BY sold_at, id")
            .param(runId)
            .query(PositionRepository::trade)
            .list());
  }

  @Override
  public StoredPositionList getWorstPositions(long runId, int maxPositions) {
    return new StoredPositionList(
        jdbcClient
            .sql(
                """
                SELECT * FROM position WHERE run_id = ? AND status = ?
                ORDER BY realised_pnl, id
                LIMIT ?
                """)
            .param(runId)
            .param(PositionStatus.CLOSED.name())
            .param(maxPositions)
            .query(PositionRepository::storedPosition)
            .list());
  }

  private int updatePosition(Position position) {
    return jdbcClient
        .sql(
            """
            UPDATE position
            SET status = ?, closed_at = ?, cost = ?, realised_pnl = ?, mae = ?, mfe = ?
            WHERE run_id = ? AND opened_at = ?
            """)
        .param(position.status().name())
        .param(optionalTimestamp(position.closedAt()))
        .param(position.cost())
        .param(position.realisedPnl())
        .param(position.mae())
        .param(position.mfe())
        .param(position.runId())
        .param(timestamp(position.openedAt()))
        .update();
  }

  private void insertPosition(Position position) {
    jdbcClient
        .sql(
            """
            INSERT INTO position (run_id, broker, ticker, status, opened_at, closed_at, cost,
                                  realised_pnl, mae, mfe)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(position.runId())
        .param(position.broker())
        .param(position.ticker())
        .param(position.status().name())
        .param(timestamp(position.openedAt()))
        .param(optionalTimestamp(position.closedAt()))
        .param(position.cost())
        .param(position.realisedPnl())
        .param(position.mae())
        .param(position.mfe())
        .update();
  }

  private OpenPosition withOpenLots(StoredPosition stored) {
    return new OpenPosition(
        stored.position(),
        new OpenLotList(
            jdbcClient
                .sql("SELECT * FROM open_lot WHERE position_id = ? ORDER BY bought_at, id")
                .param(stored.id())
                .query(PositionRepository::openLot)
                .list()));
  }

  private static StoredPosition storedPosition(ResultSet row, int rowNumber) throws SQLException {
    return new StoredPosition(
        row.getLong("id"),
        new Position(
            row.getLong("run_id"),
            row.getString("broker"),
            row.getString("ticker"),
            PositionStatus.valueOf(row.getString("status")),
            instant(row, "opened_at"),
            Optional.ofNullable(row.getObject("closed_at", OffsetDateTime.class))
                .map(OffsetDateTime::toInstant),
            row.getBigDecimal("cost"),
            row.getBigDecimal("realised_pnl"),
            row.getBigDecimal("mae"),
            row.getBigDecimal("mfe")));
  }

  private static OpenLot openLot(ResultSet row, int rowNumber) throws SQLException {
    return new OpenLot(
        row.getLong("run_id"),
        instant(row, "bought_at"),
        row.getBigDecimal("quantity"),
        row.getBigDecimal("entry_price"));
  }

  private static Trade trade(ResultSet row, int rowNumber) throws SQLException {
    return new Trade(
        row.getLong("run_id"),
        instant(row, "bought_at"),
        instant(row, "sold_at"),
        row.getBigDecimal("quantity"),
        row.getBigDecimal("entry_price"),
        row.getBigDecimal("exit_price"),
        row.getBigDecimal("fee"),
        row.getBigDecimal("realised_pnl"));
  }

  private static Object[] lotRow(long positionId, OpenLot lot) {
    return new Object[] {
      positionId, lot.runId(), timestamp(lot.boughtAt()), lot.quantity(), lot.entryPrice()
    };
  }

  private static Object[] tradeRow(long positionId, Trade trade) {
    return new Object[] {
      positionId,
      trade.runId(),
      timestamp(trade.boughtAt()),
      timestamp(trade.soldAt()),
      trade.quantity(),
      trade.entryPrice(),
      trade.exitPrice(),
      trade.fee(),
      trade.realisedPnl()
    };
  }

  private static OffsetDateTime timestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static SqlParameterValue optionalTimestamp(Optional<Instant> instant) {
    return new SqlParameterValue(
        Types.TIMESTAMP_WITH_TIMEZONE, instant.map(PositionRepository::timestamp).orElse(null));
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    return row.getObject(column, OffsetDateTime.class).toInstant();
  }
}
