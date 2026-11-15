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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code algorithm} table. The type is the {@code algorithm_type} enum and the timeframe is
 * stored in seconds. Every other setting is one JSON text, written with sorted keys and plain
 * numbers, so identical settings give identical text.
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
  public synchronized long findOrInsertAlgorithm(AlgorithmSettings settings, Instant createdAt) {
    String settingsJson = settingsJson(settings);
    String algorithmType = settings.algorithmType().name();
    long timeframeSeconds = settings.timeframe().duration().toSeconds();
    Optional<Long> storedAlgorithmId =
        jdbcClient
            .sql(
                """
                SELECT id FROM algorithm
                WHERE algorithm_type = CAST(? AS algorithm_type) AND timeframe_seconds = ?
                  AND settings = ?
                """)
            .param(algorithmType)
            .param(timeframeSeconds)
            .param(settingsJson)
            .query(Long.class)
            .optional();
    if (storedAlgorithmId.isPresent()) {
      return storedAlgorithmId.get();
    }
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO algorithm (algorithm_type, timeframe_seconds, settings, created_at)
            VALUES (CAST(? AS algorithm_type), ?, ?, ?)
            """)
        .param(algorithmType)
        .param(timeframeSeconds)
        .param(settingsJson)
        .param(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
        .update(keyHolder, "id");
    Long algorithmId = keyHolder.getKeyAs(Long.class);
    if (algorithmId == null) {
      throw new IllegalStateException("The database returned no id for an algorithm");
    }
    return algorithmId;
  }

  @Override
  public Optional<AlgorithmSettings> getAlgorithm(long algorithmId) {
    return jdbcClient
        .sql(
            """
            SELECT id, algorithm_type, timeframe_seconds, settings FROM algorithm WHERE id = ?
            """)
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
          AlgorithmType.valueOf(row.getString("algorithm_type")),
          CandleInterval.fromSeconds(row.getLong("timeframe_seconds")),
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

  private static Optional<BigDecimal> optionalNumber(JsonNode values, String name) {
    return Optional.ofNullable(values.get(name)).map(AlgorithmRepository::plainNumber);
  }

  private static BigDecimal plainNumber(JsonNode value) {
    return plainValue(value.decimalValue());
  }
}
