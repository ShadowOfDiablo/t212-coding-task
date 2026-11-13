package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmStore;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
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
          TradingAlgorithm.TIME_PERIOD,
          TradingAlgorithm.UPDATE_TIME_SECONDS,
          TradingAlgorithm.ORDER_SIZE_PERCENT,
          TradingAlgorithm.RISK_PER_TRADE_PERCENT,
          TradingAlgorithm.STOP_LOSS_PERCENT);

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
        .forEach((name, value) -> values.put(name, AlgorithmSettingValues.plainValue(value)));
    values.put(LIVE_ON_TICK, settings.liveOnTick());
    values.put(TradingAlgorithm.TIME_PERIOD, settings.timePeriod());
    settings
        .updateTimeSeconds()
        .ifPresent(seconds -> values.put(TradingAlgorithm.UPDATE_TIME_SECONDS, seconds));
    values.put(
        TradingAlgorithm.ORDER_SIZE_PERCENT,
        AlgorithmSettingValues.plainValue(settings.orderSizePercent()));
    settings
        .riskPerTradePercent()
        .ifPresent(risk ->
            values.put(
                TradingAlgorithm.RISK_PER_TRADE_PERCENT, AlgorithmSettingValues.plainValue(risk)));
    settings
        .stopLossPercent()
        .ifPresent(stop ->
            values.put(
                TradingAlgorithm.STOP_LOSS_PERCENT, AlgorithmSettingValues.plainValue(stop)));
    return SETTINGS_MAPPER.writeValueAsString(values);
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
        values.required(TradingAlgorithm.TIME_PERIOD).decimalValue().intValueExact(),
        timeframe,
        values.required(LIVE_ON_TICK).booleanValue(),
        optionalNumber(values, TradingAlgorithm.UPDATE_TIME_SECONDS).map(BigDecimal::intValueExact),
        plainNumber(values.required(TradingAlgorithm.ORDER_SIZE_PERCENT)),
        optionalNumber(values, TradingAlgorithm.RISK_PER_TRADE_PERCENT),
        optionalNumber(values, TradingAlgorithm.STOP_LOSS_PERCENT),
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
    return AlgorithmSettingValues.plainValue(value.decimalValue());
  }
}
