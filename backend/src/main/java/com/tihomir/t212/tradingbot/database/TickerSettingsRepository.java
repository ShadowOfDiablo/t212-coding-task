package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.tickersettings.AssignedSettings;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettings;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettingsList;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettingsStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** A ticker's assigned settings are its row in {@code active_algorithm}. */
@Repository
public class TickerSettingsRepository implements TickerSettingsStore {

  private final JdbcClient jdbcClient;
  private final CandleRepository candleRepository;
  private final Clock clock;

  public TickerSettingsRepository(
      JdbcClient jdbcClient, CandleRepository candleRepository, Clock clock) {
    this.jdbcClient = jdbcClient;
    this.candleRepository = candleRepository;
    this.clock = clock;
  }

  @Override
  public TickerSettingsList getAssignedSettings(String broker) {
    return new TickerSettingsList(
        jdbcClient
            .sql(
                """
                SELECT t.symbol, a.id, a.algorithm_type, a.timeframe_seconds, a.settings,
                       aa.budget, aa.settings_source, aa.tuned_run_id
                FROM active_algorithm aa
                JOIN ticker t ON t.id = aa.ticker_id
                JOIN brokers b ON b.broker_id = t.broker_id
                JOIN algorithm a ON a.id = aa.algorithm_id
                WHERE b.name = ?
                """)
            .param(broker)
            .query(TickerSettingsRepository::mapTickerSettingsRow)
            .list());
  }

  @Override
  @Transactional
  public void saveAssignedSettings(String broker, String ticker, AssignedSettings assigned) {
    long tickerId = candleRepository.insertTicker(broker, ticker);
    if (updateAssignedSettings(tickerId, assigned) == 0) {
      insertAssignedSettings(tickerId, assigned);
    }
  }

  private int updateAssignedSettings(long tickerId, AssignedSettings assigned) {
    return jdbcClient
        .sql(
            """
            UPDATE active_algorithm
            SET algorithm_id = ?, activated_at = ?, budget = ?,
                settings_source = CAST(? AS settings_source), tuned_run_id = ?
            WHERE ticker_id = ?
            """)
        .param(assigned.algorithmId())
        .param(now())
        .param(new SqlParameterValue(Types.NUMERIC, assigned.budget().orElse(null)))
        .param(assigned.source().name())
        .param(new SqlParameterValue(Types.BIGINT, assigned.tunedRunId().orElse(null)))
        .param(tickerId)
        .update();
  }

  private void insertAssignedSettings(long tickerId, AssignedSettings assigned) {
    jdbcClient
        .sql(
            """
            INSERT INTO active_algorithm (ticker_id, algorithm_id, activated_at, budget,
                                          settings_source, tuned_run_id)
            SELECT ?, ?, ?, ?, CAST(? AS settings_source), ?
            WHERE NOT EXISTS (SELECT 1 FROM active_algorithm WHERE ticker_id = ?)
            """)
        .param(tickerId)
        .param(assigned.algorithmId())
        .param(now())
        .param(new SqlParameterValue(Types.NUMERIC, assigned.budget().orElse(null)))
        .param(assigned.source().name())
        .param(new SqlParameterValue(Types.BIGINT, assigned.tunedRunId().orElse(null)))
        .param(tickerId)
        .update();
  }

  /** A row written before settings sources existed counts as picked by hand. */
  private static TickerSettings mapTickerSettingsRow(ResultSet row, int rowNumber)
      throws SQLException {
    String source = row.getString("settings_source");
    SettingsSource settingsSource = SettingsSource.MANUAL;
    if (source != null) {
      settingsSource = SettingsSource.valueOf(source);
    }
    return new TickerSettings(
        row.getString("symbol"),
        Optional.of(
            new AssignedSettings(
                row.getLong("id"),
                AlgorithmRepository.algorithmSettings(row, rowNumber),
                Optional.ofNullable(row.getBigDecimal("budget")),
                settingsSource,
                Optional.ofNullable(row.getObject("tuned_run_id", Long.class)))));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
