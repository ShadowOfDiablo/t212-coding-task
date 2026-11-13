package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AlgorithmRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
  private static final String OPTION_NAME = "window";

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired AlgorithmRepository repository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTables() {
    jdbcClient.sql("DELETE FROM active_algorithm").update();
    jdbcClient.sql("DELETE FROM training_portfolio_point").update();
    jdbcClient.sql("DELETE FROM algorithm_run").update();
    jdbcClient.sql("DELETE FROM algorithm").update();
  }

  @Test
  void storesSettingsOnceAndReadsThemBack() {
    AlgorithmSettings settings = settings(Optional.empty(), Optional.empty());

    long algorithmId = repository.findOrInsertAlgorithm(settings, CREATED_AT);

    assertThat(repository.findOrInsertAlgorithm(settings, CREATED_AT)).isEqualTo(algorithmId);
    assertThat(repository.getAlgorithm(algorithmId)).contains(settings);
    assertThat(storedRow(algorithmId))
        .isEqualTo(
            "1|5m|{\"liveOnTick\":true,\"orderSizePercent\":100,\"timePeriod\":20,\"window\":3}");
  }

  @Test
  void matchesEqualNumbersWrittenDifferentlyButNotEmptyOptionalSettings() {
    long withoutRisk =
        repository.findOrInsertAlgorithm(settings(Optional.empty(), Optional.empty()), CREATED_AT);
    long withRisk =
        repository.findOrInsertAlgorithm(
            settings(Optional.of(BigDecimal.ONE), Optional.of(new BigDecimal("0.0000001"))),
            CREATED_AT);

    assertThat(withRisk).isNotEqualTo(withoutRisk);
    assertThat(
            repository.findOrInsertAlgorithm(
                settings(Optional.of(new BigDecimal("1.00")), Optional.of(new BigDecimal("1E-7"))),
                CREATED_AT))
        .isEqualTo(withRisk);
    assertThat(repository.getAlgorithm(withRisk))
        .hasValueSatisfying(
            stored -> assertThat(stored.stopLossPercent()).contains(new BigDecimal("1E-7")));
  }

  @Test
  void findsNoAlgorithmForAnUnknownId() {
    assertThat(repository.getAlgorithm(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  void refusesToReadUnreadableSettings() {
    long algorithmId =
        jdbcClient
            .sql(
                """
                INSERT INTO algorithm (algorithm_type, timeframe, settings, created_at)
                VALUES (1, '5m', '{"liveOnTick":true}', ?) RETURNING id
                """)
            .param(OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC))
            .query(Long.class)
            .single();

    assertThatThrownBy(() -> repository.getAlgorithm(algorithmId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(algorithmId));
  }

  private String storedRow(long algorithmId) {
    return jdbcClient
        .sql(
            "SELECT algorithm_type || '|' || timeframe || '|' || settings FROM algorithm"
                + " WHERE id = ?")
        .param(algorithmId)
        .query(String.class)
        .single();
  }

  private static AlgorithmSettings settings(
      Optional<BigDecimal> riskPerTradePercent, Optional<BigDecimal> stopLossPercent) {
    return new AlgorithmSettings(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        20,
        CandleInterval.FIVE_MINUTES,
        true,
        Optional.empty(),
        new BigDecimal("100"),
        riskPerTradePercent,
        stopLossPercent,
        new AlgorithmSettingValues(Map.of(OPTION_NAME, new BigDecimal("3"))));
  }
}
