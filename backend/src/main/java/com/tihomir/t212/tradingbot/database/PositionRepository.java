package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.PositionStore;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import com.tihomir.t212.tradingbot.position.StoredPosition;
import com.tihomir.t212.tradingbot.position.StoredPositionList;
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
            .sql(
                """
                SELECT p.*, b.name AS broker, t.symbol AS ticker
                FROM position p
                JOIN algorithm_run r ON r.id = p.run_id
                JOIN ticker t ON t.id = r.ticker_id
                JOIN brokers b ON b.broker_id = t.broker_id
                WHERE p.run_id = ? AND p.status = CAST(? AS position_status)
                ORDER BY p.realised_pnl, p.id
                LIMIT ?
                """)
            .param(runId)
            .param(PositionStatus.CLOSED.name())
            .param(maxPositions)
            .query(PositionRepository::mapPositionRow)
            .list());
  }

  private int updatePosition(Position position) {
    return jdbcClient
        .sql(
            """
            UPDATE position
            SET status = CAST(? AS position_status),
                closed_at = ?, cost = ?, realised_pnl = ?, mae = ?, mfe = ?
            WHERE run_id = ? AND opened_at = ?
            """)
        .param(position.status().name())
        .param(toOptionalUtcTimestamp(position.closedAt()))
        .param(position.cost())
        .param(position.realisedPnl())
        .param(position.mae())
        .param(position.mfe())
        .param(position.runId())
        .param(toUtcTimestamp(position.openedAt()))
        .update();
  }

  private void insertPosition(Position position) {
    jdbcClient
        .sql(
            """
            INSERT INTO position (run_id, status, opened_at, closed_at, cost,
                                  realised_pnl, mae, mfe)
            VALUES (?, CAST(? AS position_status), ?, ?, ?, ?, ?, ?)
            """)
        .param(position.runId())
        .param(position.status().name())
        .param(toUtcTimestamp(position.openedAt()))
        .param(toOptionalUtcTimestamp(position.closedAt()))
        .param(position.cost())
        .param(position.realisedPnl())
        .param(position.mae())
        .param(position.mfe())
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
            row.getBigDecimal("mfe")));
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
