package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.bot.AlgorithmRunStore;
import com.tihomir.t212.tradingbot.bot.PortfolioPoint;
import com.tihomir.t212.tradingbot.bot.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.RecordedRun;
import com.tihomir.t212.tradingbot.bot.RecordedRunList;
import com.tihomir.t212.tradingbot.bot.RunCounts;
import com.tihomir.t212.tradingbot.bot.RunEnd;
import com.tihomir.t212.tradingbot.bot.RunEvent;
import com.tihomir.t212.tradingbot.bot.RunSettingsChange;
import com.tihomir.t212.tradingbot.bot.RunSettingsChangeList;
import com.tihomir.t212.tradingbot.bot.RunStart;
import com.tihomir.t212.tradingbot.bot.RunStatus;
import com.tihomir.t212.tradingbot.bot.StoredRunDetails;
import com.tihomir.t212.tradingbot.mode.BotMode;
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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AlgorithmRunRepository implements AlgorithmRunStore {

  private static final int MAX_RUNS_PER_LIST = 50;

  private final JdbcClient jdbcClient;
  private final JdbcTemplate jdbcTemplate;

  public AlgorithmRunRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
    this.jdbcClient = jdbcClient;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public long insertStartedRun(RunStart start) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO algorithm_run (mode, broker, ticker, status, started_at, from_time, to_time,
                                       starting_value, buy_count, sell_count, stop_loss_count,
                                       refused_order_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0)
            """)
        .param(start.mode().name())
        .param(start.broker())
        .param(start.ticker())
        .param(RunStatus.STARTED.name())
        .param(timestamp(start.startedAt()))
        .param(optionalTimestamp(start.from()))
        .param(optionalTimestamp(start.to()))
        .param(start.startingValue())
        .update(keyHolder, "id");
    Long runId = keyHolder.getKeyAs(Long.class);
    if (runId == null) {
      throw new IllegalStateException("The database returned no id for the run");
    }
    insertSettingsChange(
        runId, new RunSettingsChange(RunEvent.STARTED, start.startedAt(), start.settingsId()));
    return runId;
  }

  @Override
  public void insertSettingsChange(long runId, RunSettingsChange change) {
    jdbcClient
        .sql(
            """
            INSERT INTO algorithm_run_settings (run_id, recorded_at, event, settings_id)
            VALUES (?, ?, ?, ?)
            """)
        .param(runId)
        .param(timestamp(change.recordedAt()))
        .param(change.event().name())
        .param(change.settingsId())
        .update();
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
                SET status = ?, finished_at = ?, final_value = ?, buy_count = ?, sell_count = ?,
                    stop_loss_count = ?, refused_order_count = ?, failure_reason = ?
                WHERE id = ? AND status = ?
                """)
            .param(end.status().name())
            .param(timestamp(end.finishedAt()))
            .param(new SqlParameterValue(Types.NUMERIC, end.finalValue().orElse(null)))
            .param(counts.buyCount())
            .param(counts.sellCount())
            .param(counts.stopLossCount())
            .param(counts.refusedOrderCount())
            .param(new SqlParameterValue(Types.VARCHAR, end.failureReason().orElse(null)))
            .param(runId)
            .param(RunStatus.STARTED.name())
            .update();
    if (updated == 0) {
      throw new IllegalStateException("Run " + runId + " is not running");
    }
    insertSettingsChange(
        runId, new RunSettingsChange(RunEvent.COMPLETED, end.finishedAt(), end.settingsId()));
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO training_portfolio_point (run_id, point_time, cash, value)
        VALUES (?, ?, ?, ?)
        """,
        end.points().points().stream().map(point -> pointRow(runId, point)).toList());
  }

  @Override
  @Transactional
  public int stopUnfinishedRuns(Instant stoppedAt, String reason) {
    OffsetDateTime stoppedTime = timestamp(stoppedAt);
    jdbcClient
        .sql(
            """
            INSERT INTO algorithm_run_settings (run_id, recorded_at, event, settings_id)
            SELECT s.run_id, ?, ?, s.settings_id
            FROM algorithm_run_settings s
            JOIN algorithm_run r ON r.id = s.run_id
            WHERE r.status = ?
              AND s.recorded_at = (SELECT MAX(m.recorded_at) FROM algorithm_run_settings m
                                   WHERE m.run_id = s.run_id)
            """)
        .param(stoppedTime)
        .param(RunEvent.COMPLETED.name())
        .param(RunStatus.STARTED.name())
        .update();
    return jdbcClient
        .sql(
            """
            UPDATE algorithm_run SET status = ?, finished_at = ?, failure_reason = ?
            WHERE status = ?
            """)
        .param(RunStatus.STOPPED.name())
        .param(stoppedTime)
        .param(reason)
        .param(RunStatus.STARTED.name())
        .update();
  }

  @Override
  public Optional<StoredRunDetails> getRun(long runId) {
    return jdbcClient
        .sql("SELECT * FROM algorithm_run WHERE id = ?")
        .param(runId)
        .query(AlgorithmRunRepository::recordedRun)
        .optional()
        .map(this::withDetails);
  }

  @Override
  public RecordedRunList getRuns(String broker) {
    return new RecordedRunList(
        jdbcClient
            .sql("SELECT * FROM algorithm_run WHERE broker = ? ORDER BY id DESC LIMIT ?")
            .param(broker)
            .param(MAX_RUNS_PER_LIST)
            .query(AlgorithmRunRepository::recordedRun)
            .list());
  }

  private StoredRunDetails withDetails(RecordedRun run) {
    RunSettingsChangeList changes =
        new RunSettingsChangeList(
            jdbcClient
                .sql(
                    """
                    SELECT recorded_at, event, settings_id FROM algorithm_run_settings
                    WHERE run_id = ? ORDER BY recorded_at, event DESC
                    """)
                .param(run.id())
                .query(AlgorithmRunRepository::settingsChange)
                .list());
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
    return new StoredRunDetails(run, changes, points);
  }

  private static RecordedRun recordedRun(ResultSet row, int rowNumber) throws SQLException {
    return new RecordedRun(
        row.getLong("id"),
        BotMode.valueOf(row.getString("mode")),
        row.getString("broker"),
        row.getString("ticker"),
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
            row.getInt("refused_order_count")),
        Optional.ofNullable(row.getString("failure_reason")));
  }

  private static RunSettingsChange settingsChange(ResultSet row, int rowNumber)
      throws SQLException {
    return new RunSettingsChange(
        RunEvent.valueOf(row.getString("event")),
        instant(row, "recorded_at"),
        row.getLong("settings_id"));
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
