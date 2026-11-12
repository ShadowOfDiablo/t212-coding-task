package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * The shared {@code algorithm_settings} table, used by each algorithm's own settings store. The
 * match compares NULL with NULL too, in plain SQL, so identical settings find the same row.
 */
@Repository
public class AlgorithmSettingsRepository {

  /** Matches a row of {@code algorithm_settings a} with the values of {@link #matchValues}. */
  static final String SHARED_SETTINGS_MATCH =
      """
      a.algorithm_id = ? AND a.time_period = ? AND a.timeframe = ? AND a.live_on_tick = ?
        AND (a.update_time_seconds = ? OR (a.update_time_seconds IS NULL AND ? IS NULL))
        AND a.order_size_percent = ?
        AND (a.risk_per_trade_percent = ? OR (a.risk_per_trade_percent IS NULL AND ? IS NULL))
        AND (a.stop_loss_percent = ? OR (a.stop_loss_percent IS NULL AND ? IS NULL))
      """;

  private final JdbcClient jdbcClient;

  public AlgorithmSettingsRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /** The values for {@link #SHARED_SETTINGS_MATCH}, in its order. */
  List<Object> matchValues(AlgorithmSettings settings) {
    SqlParameterValue updateTime = integerValue(settings.updateTimeSeconds());
    SqlParameterValue risk = numericValue(settings.riskPerTradePercent());
    SqlParameterValue stopLoss = numericValue(settings.stopLossPercent());
    return List.of(
        settings.algorithmId(),
        settings.timePeriod(),
        settings.timeframe().name(),
        settings.liveOnTick(),
        updateTime,
        updateTime,
        settings.orderSizePercent(),
        risk,
        risk,
        stopLoss,
        stopLoss);
  }

  long insertSharedSettings(AlgorithmSettings settings) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO algorithm_settings (algorithm_id, time_period, timeframe, live_on_tick,
                                            update_time_seconds, order_size_percent,
                                            risk_per_trade_percent, stop_loss_percent)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(settings.algorithmId())
        .param(settings.timePeriod())
        .param(settings.timeframe().name())
        .param(settings.liveOnTick())
        .param(integerValue(settings.updateTimeSeconds()))
        .param(settings.orderSizePercent())
        .param(numericValue(settings.riskPerTradePercent()))
        .param(numericValue(settings.stopLossPercent()))
        .update(keyHolder, "id");
    Long settingsId = keyHolder.getKeyAs(Long.class);
    if (settingsId == null) {
      throw new IllegalStateException("The database returned no id for the algorithm settings");
    }
    return settingsId;
  }

  /** Reads the shared columns of a row selected as {@code a.*}. */
  static AlgorithmSettings sharedSettings(ResultSet row, AlgorithmSettingValues options)
      throws SQLException {
    return new AlgorithmSettings(
        row.getInt("algorithm_id"),
        row.getInt("time_period"),
        CandleInterval.valueOf(row.getString("timeframe")),
        row.getBoolean("live_on_tick"),
        Optional.ofNullable(row.getObject("update_time_seconds", Integer.class)),
        AlgorithmSettingValues.plainValue(row.getBigDecimal("order_size_percent")),
        Optional.ofNullable(row.getBigDecimal("risk_per_trade_percent"))
            .map(AlgorithmSettingValues::plainValue),
        Optional.ofNullable(row.getBigDecimal("stop_loss_percent"))
            .map(AlgorithmSettingValues::plainValue),
        options);
  }

  private static SqlParameterValue integerValue(Optional<Integer> value) {
    return new SqlParameterValue(Types.INTEGER, value.orElse(null));
  }

  private static SqlParameterValue numericValue(Optional<BigDecimal> value) {
    return new SqlParameterValue(Types.NUMERIC, value.orElse(null));
  }
}
