package com.tihomir.t212.tradingbot.database;

import static com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues.plainValue;
import static com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm.ORDER_SIZE_PERCENT;
import static com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm.RISK_PER_TRADE_PERCENT;
import static com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm.STOP_LOSS_PERCENT;
import static com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm.TIME_PERIOD;
import static com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm.UPDATE_TIME_SECONDS;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmStore;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code algorithm} table. The type is stored as a number and the timeframe as its code. Every
 * other setting is one JSON text, written with sorted keys and plain numbers, so identical settings
 * give identical text.
 */
@Repository
public class AlgorithmRepository implements AlgorithmStore {

  static final String LIVE_ON_TICK = "liveOnTick";

  private static final Set<String> SHARED_NAMES =
      Set.of(
          LIVE_ON_TICK,
          TIME_PERIOD,
          UPDATE_TIME_SECONDS,
          ORDER_SIZE_PERCENT,
          RISK_PER_TRADE_PERCENT,
          STOP_LOSS_PERCENT);

  private static final int SIMPLE_MOVING_AVERAGE_NUMBER = 1;

  private static final JsonMapper SETTINGS_MAPPER =
      JsonMapper.builder()
          .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .build();

  private final JdbcClient jdbcClient;

  public AlgorithmRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  @Transactional
  public long findOrInsertAlgorithm(AlgorithmSettings settings, Instant createdAt) {
    String settingsJson = settingsJson(settings);
    jdbcClient
        .sql(
            """
            INSERT INTO algorithm (algorithm_type, timeframe, settings, created_at)
            SELECT ?, ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM algorithm
                              WHERE algorithm_type = ? AND timeframe = ? AND settings = ?)
            """)
        .param(algorithmTypeNumber(settings.algorithmType()))
        .param(settings.timeframe().code())
        .param(settingsJson)
        .param(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
        .param(algorithmTypeNumber(settings.algorithmType()))
        .param(settings.timeframe().code())
        .param(settingsJson)
        .update();
    return jdbcClient
        .sql("SELECT id FROM algorithm WHERE algorithm_type = ? AND timeframe = ? AND settings = ?")
        .param(algorithmTypeNumber(settings.algorithmType()))
        .param(settings.timeframe().code())
        .param(settingsJson)
        .query(Long.class)
        .single();
  }

  @Override
  public Optional<AlgorithmSettings> getAlgorithm(long algorithmId) {
    return jdbcClient
        .sql("SELECT id, algorithm_type, timeframe, settings FROM algorithm WHERE id = ?")
        .param(algorithmId)
        .query(AlgorithmRepository::algorithmSettings)
        .optional();
  }

  static String settingsJson(AlgorithmSettings settings) {
    Map<String, Object> values = new TreeMap<>();
    settings
        .options()
        .values()
        .forEach((name, value) -> putNumber(values, name, Optional.of(value)));
    values.put(LIVE_ON_TICK, settings.liveOnTick());
    values.put(TIME_PERIOD, settings.timePeriod());
    putNumber(values, UPDATE_TIME_SECONDS, settings.updateTimeSeconds().map(BigDecimal::valueOf));
    putNumber(values, ORDER_SIZE_PERCENT, Optional.of(settings.orderSizePercent()));
    putNumber(values, RISK_PER_TRADE_PERCENT, settings.riskPerTradePercent());
    putNumber(values, STOP_LOSS_PERCENT, settings.stopLossPercent());
    return SETTINGS_MAPPER.writeValueAsString(values);
  }

  /** Leaves an empty setting out and writes a number without trailing zeros. */
  private static void putNumber(
      Map<String, Object> values, String name, Optional<BigDecimal> value) {
    value.ifPresent(number -> values.put(name, plainValue(number)));
  }

  private static AlgorithmSettings algorithmSettings(ResultSet row, int rowNumber)
      throws SQLException {
    long algorithmId = row.getLong("id");
    try {
      return algorithmSettings(
          algorithmType(row.getInt("algorithm_type")),
          CandleInterval.fromCode(row.getString("timeframe")),
          SETTINGS_MAPPER.readTree(row.getString("settings")));
    } catch (JacksonException | IllegalArgumentException | ArithmeticException e) {
      throw new IllegalStateException(
          "Stored algorithm " + algorithmId + " has unreadable settings", e);
    }
  }

  private static AlgorithmSettings algorithmSettings(
      AlgorithmType algorithmType, CandleInterval timeframe, JsonNode values) {
    return new AlgorithmSettings(
        algorithmType,
        values.required(TIME_PERIOD).decimalValue().intValueExact(),
        timeframe,
        values.required(LIVE_ON_TICK).booleanValue(),
        optionalNumber(values, UPDATE_TIME_SECONDS).map(BigDecimal::intValueExact),
        plainNumber(values.required(ORDER_SIZE_PERCENT)),
        optionalNumber(values, RISK_PER_TRADE_PERCENT),
        optionalNumber(values, STOP_LOSS_PERCENT),
        new AlgorithmSettingValues(
            values.properties().stream()
                .filter(property -> !SHARED_NAMES.contains(property.getKey()))
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey, property -> plainNumber(property.getValue())))));
  }

  static int algorithmTypeNumber(AlgorithmType algorithmType) {
    return switch (algorithmType) {
      case SIMPLE_MOVING_AVERAGE -> SIMPLE_MOVING_AVERAGE_NUMBER;
    };
  }

  static AlgorithmType algorithmType(int algorithmTypeNumber) {
    return switch (algorithmTypeNumber) {
      case SIMPLE_MOVING_AVERAGE_NUMBER -> AlgorithmType.SIMPLE_MOVING_AVERAGE;
      default ->
          throw new IllegalArgumentException(
              "Unknown algorithm type number: " + algorithmTypeNumber);
    };
  }

  private static Optional<BigDecimal> optionalNumber(JsonNode values, String name) {
    return Optional.ofNullable(values.get(name)).map(AlgorithmRepository::plainNumber);
  }

  private static BigDecimal plainNumber(JsonNode value) {
    return plainValue(value.decimalValue());
  }
}
