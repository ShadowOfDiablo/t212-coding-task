package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionPrices;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.PositionStore;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import com.tihomir.t212.tradingbot.position.StoredPosition;
import com.tihomir.t212.tradingbot.position.StoredPositionList;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PositionRepository implements PositionStore {

  private static final String SELECT_POSITIONS_OF_RUN =
      """
      SELECT p.*, b.name AS broker, t.symbol AS ticker
      FROM position p
      JOIN algorithm_run r ON r.id = p.run_id
      JOIN ticker t ON t.id = r.ticker_id
      JOIN brokers b ON b.broker_id = t.broker_id
      WHERE p.run_id = ?
      """;

  private static final String WORST_CLOSED_FIRST =
      """
      AND p.status = CAST(? AS position_status)
      ORDER BY p.realised_pnl, p.id
      LIMIT ?
      """;

  private static final String NEWEST_FIRST = "ORDER BY p.opened_at DESC, p.id DESC";

  private final JdbcClient jdbcClient;

  public PositionRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  @Transactional
  public void savePosition(PositionWrite write) {
    Position position = write.position();
    if (updatePosition(position) == 0) {
      insertPosition(position);
    }
  }

  @Override
  public StoredPositionList getWorstPositions(long runId, int maxPositions) {
    return new StoredPositionList(
        jdbcClient
            .sql(SELECT_POSITIONS_OF_RUN + WORST_CLOSED_FIRST)
            .param(runId)
            .param(PositionStatus.CLOSED.name())
            .param(maxPositions)
            .query(PositionRepository::mapPositionRow)
            .list());
  }

  @Override
  public StoredPositionList getPositions(long runId) {
    return new StoredPositionList(
        jdbcClient
            .sql(SELECT_POSITIONS_OF_RUN + NEWEST_FIRST)
            .param(runId)
            .query(PositionRepository::mapPositionRow)
            .list());
  }

  @Override
  public int closeOpenPositions(String broker, Instant closedAt) {
    return jdbcClient
        .sql(
            """
            UPDATE position SET status = CAST(? AS position_status), closed_at = ?
            WHERE status = CAST(? AS position_status)
              AND run_id IN (SELECT r.id FROM algorithm_run r
                             JOIN ticker t ON t.id = r.ticker_id
                             JOIN brokers b ON b.broker_id = t.broker_id
                             WHERE b.name = ?)
            """)
        .param(PositionStatus.CLOSED.name())
        .param(toUtcTimestamp(closedAt))
        .param(PositionStatus.OPEN.name())
        .param(broker)
        .update();
  }

  private int updatePosition(Position position) {
    return jdbcClient
        .sql(
            """
            UPDATE position
            SET status = CAST(? AS position_status),
                closed_at = ?, cost = ?, realised_pnl = ?, mae = ?, mfe = ?,
                quantity = ?, entry_price = ?, exit_price = ?
            WHERE run_id = ? AND opened_at = ?
            """)
        .param(position.status().name())
        .param(toOptionalUtcTimestamp(position.closedAt()))
        .param(position.cost())
        .param(position.realisedPnl())
        .param(position.mae())
        .param(position.mfe())
        .param(toOptionalNumber(position.prices().map(PositionPrices::quantity)))
        .param(toOptionalNumber(position.prices().map(PositionPrices::entryPrice)))
        .param(toOptionalNumber(position.prices().flatMap(PositionPrices::exitPrice)))
        .param(position.runId())
        .param(toUtcTimestamp(position.openedAt()))
        .update();
  }

  private void insertPosition(Position position) {
    jdbcClient
        .sql(
            """
            INSERT INTO position (run_id, status, opened_at, closed_at, cost,
                                  realised_pnl, mae, mfe, quantity, entry_price, exit_price)
            VALUES (?, CAST(? AS position_status), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(position.runId())
        .param(position.status().name())
        .param(toUtcTimestamp(position.openedAt()))
        .param(toOptionalUtcTimestamp(position.closedAt()))
        .param(position.cost())
        .param(position.realisedPnl())
        .param(position.mae())
        .param(position.mfe())
        .param(toOptionalNumber(position.prices().map(PositionPrices::quantity)))
        .param(toOptionalNumber(position.prices().map(PositionPrices::entryPrice)))
        .param(toOptionalNumber(position.prices().flatMap(PositionPrices::exitPrice)))
        .update();
  }

  private static StoredPosition mapPositionRow(ResultSet row, int rowNumber) throws SQLException {
    return new StoredPosition(
        row.getLong("id"),
        new Position(
            row.getLong("run_id"),
            row.getString("broker"),
            row.getString("ticker"),
            PositionStatus.valueOf(row.getString("status")),
            readInstant(row, "opened_at"),
            Optional.ofNullable(row.getObject("closed_at", OffsetDateTime.class))
                .map(OffsetDateTime::toInstant),
            row.getBigDecimal("cost"),
            row.getBigDecimal("realised_pnl"),
            row.getBigDecimal("mae"),
            row.getBigDecimal("mfe"),
            readPrices(row)));
  }

  /** Empty for a position stored before quantities and prices were kept (V12). */
  private static Optional<PositionPrices> readPrices(ResultSet row) throws SQLException {
    BigDecimal quantity = row.getBigDecimal("quantity");
    BigDecimal entryPrice = row.getBigDecimal("entry_price");
    if (quantity == null || entryPrice == null) {
      return Optional.empty();
    }
    return Optional.of(
        new PositionPrices(
            quantity, entryPrice, Optional.ofNullable(row.getBigDecimal("exit_price"))));
  }

  private static SqlParameterValue toOptionalNumber(Optional<BigDecimal> number) {
    return new SqlParameterValue(Types.NUMERIC, number.orElse(null));
  }

  private static OffsetDateTime toUtcTimestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static SqlParameterValue toOptionalUtcTimestamp(Optional<Instant> instant) {
    return new SqlParameterValue(
        Types.TIMESTAMP_WITH_TIMEZONE,
        instant.map(PositionRepository::toUtcTimestamp).orElse(null));
  }

  private static Instant readInstant(ResultSet row, String column) throws SQLException {
    return row.getObject(column, OffsetDateTime.class).toInstant();
  }
}
