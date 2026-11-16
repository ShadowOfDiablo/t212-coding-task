package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.bot.model.PortfolioPoint;
import com.tihomir.t212.tradingbot.bot.run.AlgorithmRunStore;
import com.tihomir.t212.tradingbot.bot.run.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.run.RecordedRun;
import com.tihomir.t212.tradingbot.bot.run.RecordedRunList;
import com.tihomir.t212.tradingbot.bot.run.RunCounts;
import com.tihomir.t212.tradingbot.bot.run.RunEnd;
import com.tihomir.t212.tradingbot.bot.run.RunStart;
import com.tihomir.t212.tradingbot.bot.run.RunStatus;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.run.StoredRunDetails;
import com.tihomir.t212.tradingbot.mode.BotMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AlgorithmRunRepository implements AlgorithmRunStore {

  private static final int MAX_RUNS_PER_LIST = 50;

  private static final String SELECT_RUNS =
      """
      SELECT r.*, b.name AS broker, t.symbol AS ticker
      FROM algorithm_run r
      JOIN ticker t ON t.id = r.ticker_id
      JOIN brokers b ON b.broker_id = t.broker_id
      """;

  private final JdbcClient jdbcClient;
  private final JdbcTemplate jdbcTemplate;
  private final CandleRepository candleRepository;
  private final PositionRepository positionRepository;

  public AlgorithmRunRepository(
      JdbcClient jdbcClient,
      JdbcTemplate jdbcTemplate,
      CandleRepository candleRepository,
      PositionRepository positionRepository) {
    this.jdbcClient = jdbcClient;
    this.jdbcTemplate = jdbcTemplate;
    this.candleRepository = candleRepository;
    this.positionRepository = positionRepository;
  }

  @Override
  @Transactional
  public long insertStartedRun(RunStart start) {
    long tickerId = candleRepository.insertTicker(start.broker(), start.ticker());
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO algorithm_run (ticker_id, algorithm_id, mode, settings_source, status,
                                       started_at, from_time, to_time, starting_value, buy_count,
                                       sell_count, stop_loss_count, take_profit_count,
                                       refused_order_count)
            VALUES (?, ?, CAST(? AS bot_mode), CAST(? AS settings_source),
                    CAST(? AS run_status), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(tickerId)
        .param(start.algorithmId())
        .param(start.mode().name())
        .param(start.settingsSource().name())
        .param(RunStatus.STARTED.name())
        .param(timestamp(start.startedAt()))
        .param(optionalTimestamp(start.from()))
        .param(optionalTimestamp(start.to()))
        .param(start.startingValue())
        .param(RunCounts.NONE.buyCount())
        .param(RunCounts.NONE.sellCount())
        .param(RunCounts.NONE.stopLossCount())
        .param(RunCounts.NONE.takeProfitCount())
        .param(RunCounts.NONE.refusedOrderCount())
        .update(keyHolder, "id");
    Long runId = keyHolder.getKeyAs(Long.class);
    if (runId == null) {
      throw new IllegalStateException("The database returned no id for the run");
    }
    if (start.mode() == BotMode.TRADING) {
      updateActiveAlgorithm(tickerId, start);
    }
    return runId;
  }

  @Override
  @Transactional
  public void finishRun(long runId, RunEnd end) {
    RunCounts counts = end.counts();
    int updated =
        jdbcClient
            .sql(
                """
                UPDATE algorithm_run
                SET status = CAST(? AS run_status), finished_at = ?,
                    final_value = ?, buy_count = ?, sell_count = ?, stop_loss_count = ?,
                    take_profit_count = ?,
                    refused_order_count = ?, failure_reason = ?
                WHERE id = ? AND status = CAST(? AS run_status)
                """)
            .param(end.status().name())
            .param(timestamp(end.finishedAt()))
            .param(new SqlParameterValue(Types.NUMERIC, end.finalValue().orElse(null)))
            .param(counts.buyCount())
            .param(counts.sellCount())
            .param(counts.stopLossCount())
            .param(counts.takeProfitCount())
            .param(counts.refusedOrderCount())
            .param(new SqlParameterValue(Types.VARCHAR, end.failureReason().orElse(null)))
            .param(runId)
            .param(RunStatus.STARTED.name())
            .update();
    if (updated == 0) {
      throw new IllegalStateException("Run " + runId + " is not running");
    }
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO training_portfolio_point (run_id, point_time, cash, value)
        VALUES (?, ?, ?, ?)
        """,
        end.points().points().stream().map(point -> pointRow(runId, point)).toList());
    end.positionWrites().writes().forEach(positionRepository::savePosition);
  }

  @Override
  @Transactional
  public long replaceRun(long runId, RunEnd end, RunStart next) {
    finishRun(runId, end);
    return insertStartedRun(next);
  }

  @Override
  public int stopUnfinishedRuns(Instant stoppedAt, String reason) {
    return jdbcClient
        .sql(
            """
            UPDATE algorithm_run
            SET status = CAST(? AS run_status), finished_at = ?,
                failure_reason = ?
            WHERE status = CAST(? AS run_status)
            """)
        .param(RunStatus.STOPPED.name())
        .param(timestamp(stoppedAt))
        .param(reason)
        .param(RunStatus.STARTED.name())
        .update();
  }

  @Override
  public Optional<StoredRunDetails> getRun(long runId) {
    return jdbcClient
        .sql(SELECT_RUNS + "WHERE r.id = ?")
        .param(runId)
        .query(AlgorithmRunRepository::recordedRun)
        .optional()
        .map(this::withPoints);
  }

  @Override
  public RecordedRunList getRuns(String broker) {
    return new RecordedRunList(
        jdbcClient
            .sql(SELECT_RUNS + "WHERE b.name = ? ORDER BY r.id DESC LIMIT ?")
            .param(broker)
            .param(MAX_RUNS_PER_LIST)
            .query(AlgorithmRunRepository::recordedRun)
            .list());
  }

  @Override
  @Transactional
  public int deleteTunedRunsExceptBest(String broker, String ticker, long keptRunId) {
    List<Long> deletedRunIds =
        jdbcClient
            .sql(
                """
                SELECT r.id FROM algorithm_run r
                JOIN ticker t ON t.id = r.ticker_id
                JOIN brokers b ON b.broker_id = t.broker_id
                WHERE b.name = :broker AND t.symbol = :ticker
                  AND r.mode = CAST(:mode AS bot_mode)
                  AND r.settings_source = CAST(:source AS settings_source)
                  AND r.status <> CAST(:started AS run_status)
                  AND r.id <> :keptRunId
                  AND r.id <> COALESCE(
                    (SELECT r2.id FROM algorithm_run r2
                     WHERE r2.ticker_id = r.ticker_id AND r2.mode = r.mode
                       AND r2.settings_source = r.settings_source AND r2.final_value IS NOT NULL
                     ORDER BY r2.final_value / r2.starting_value DESC, r2.id DESC
                     LIMIT 1), -1)
                  AND r.id NOT IN (
                    SELECT tuned_run_id FROM active_algorithm WHERE tuned_run_id IS NOT NULL)
                """)
            .param("broker", broker)
            .param("ticker", ticker)
            .param("mode", BotMode.TRAINING.name())
            .param("source", SettingsSource.AUTOMATIC.name())
            .param("started", RunStatus.STARTED.name())
            .param("keptRunId", keptRunId)
            .query(Long.class)
            .list();
    if (deletedRunIds.isEmpty()) {
      return 0;
    }
    deleteFromRunTable(
        "DELETE FROM training_portfolio_point WHERE run_id IN (:ids)", deletedRunIds);
    deleteFromRunTable("DELETE FROM position WHERE run_id IN (:ids)", deletedRunIds);
    deleteFromRunTable("UPDATE orders SET run_id = NULL WHERE run_id IN (:ids)", deletedRunIds);
    return deleteFromRunTable("DELETE FROM algorithm_run WHERE id IN (:ids)", deletedRunIds);
  }

  private int deleteFromRunTable(String sql, List<Long> runIds) {
    return jdbcClient.sql(sql).param("ids", runIds).update();
  }

  private void updateActiveAlgorithm(long tickerId, RunStart start) {
    OffsetDateTime activatedAt = timestamp(start.startedAt());
    int updated =
        jdbcClient
            .sql(
                """
                UPDATE active_algorithm SET algorithm_id = ?, activated_at = ?
                WHERE ticker_id = ?
                """)
            .param(start.algorithmId())
            .param(activatedAt)
            .param(tickerId)
            .update();
    if (updated > 0) {
      return;
    }
    jdbcClient
        .sql(
            """
            INSERT INTO active_algorithm (ticker_id, algorithm_id, activated_at)
            VALUES (?, ?, ?)
            """)
        .param(tickerId)
        .param(start.algorithmId())
        .param(activatedAt)
        .update();
  }

  private StoredRunDetails withPoints(RecordedRun run) {
    PortfolioPointList points =
        new PortfolioPointList(
            jdbcClient
                .sql(
                    """
                    SELECT point_time, cash, value FROM training_portfolio_point
                    WHERE run_id = ? ORDER BY point_time
                    """)
                .param(run.id())
                .query(AlgorithmRunRepository::portfolioPoint)
                .list());
    return new StoredRunDetails(run, points);
  }

  private static RecordedRun recordedRun(ResultSet row, int rowNumber) throws SQLException {
    return new RecordedRun(
        row.getLong("id"),
        BotMode.valueOf(row.getString("mode")),
        row.getString("broker"),
        row.getString("ticker"),
        row.getLong("algorithm_id"),
        SettingsSource.valueOf(row.getString("settings_source")),
        RunStatus.valueOf(row.getString("status")),
        instant(row, "started_at"),
        optionalInstant(row, "finished_at"),
        optionalInstant(row, "from_time"),
        optionalInstant(row, "to_time"),
        row.getBigDecimal("starting_value"),
        Optional.ofNullable(row.getBigDecimal("final_value")),
        new RunCounts(
            row.getInt("buy_count"),
            row.getInt("sell_count"),
            row.getInt("stop_loss_count"),
            row.getInt("take_profit_count"),
            row.getInt("refused_order_count")),
        Optional.ofNullable(row.getString("failure_reason")));
  }

  private static PortfolioPoint portfolioPoint(ResultSet row, int rowNumber) throws SQLException {
    return new PortfolioPoint(
        instant(row, "point_time"), row.getBigDecimal("cash"), row.getBigDecimal("value"));
  }

  private static Object[] pointRow(long runId, PortfolioPoint point) {
    return new Object[] {runId, timestamp(point.time()), point.cash(), point.value()};
  }

  private static OffsetDateTime timestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static SqlParameterValue optionalTimestamp(Optional<Instant> instant) {
    return new SqlParameterValue(
        Types.TIMESTAMP_WITH_TIMEZONE, instant.map(AlgorithmRunRepository::timestamp).orElse(null));
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    return row.getObject(column, OffsetDateTime.class).toInstant();
  }

  private static Optional<Instant> optionalInstant(ResultSet row, String column)
      throws SQLException {
    return Optional.ofNullable(row.getObject(column, OffsetDateTime.class))
        .map(OffsetDateTime::toInstant);
  }
}
