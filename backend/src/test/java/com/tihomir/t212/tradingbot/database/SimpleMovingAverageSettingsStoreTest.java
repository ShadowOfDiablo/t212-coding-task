package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import java.math.BigDecimal;
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
class SimpleMovingAverageSettingsStoreTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired SimpleMovingAverageSettingsStore store;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTables() {
    jdbcClient.sql("DELETE FROM sma_settings").update();
    jdbcClient.sql("DELETE FROM algorithm_settings").update();
  }

  @Test
  void storesSettingsOnceAndReadsThemBack() {
    AlgorithmSettings settings = settings(Optional.empty(), Optional.empty());

    long settingsId = store.findOrInsertSettings(settings);

    assertThat(store.findOrInsertSettings(settings)).isEqualTo(settingsId);
    assertThat(store.getSettings(settingsId)).contains(settings);
    assertThat(count("algorithm_settings")).isEqualTo(1);
    assertThat(count("sma_settings")).isEqualTo(1);
  }

  @Test
  void matchesEmptyOptionalSettingsOnlyWithEmptyOnes() {
    long withoutRisk = store.findOrInsertSettings(settings(Optional.empty(), Optional.empty()));
    long withRisk =
        store.findOrInsertSettings(
            settings(Optional.of(BigDecimal.ONE), Optional.of(BigDecimal.TWO)));

    assertThat(withRisk).isNotEqualTo(withoutRisk);
    assertThat(
            store.findOrInsertSettings(
                settings(Optional.of(new BigDecimal("1.00")), Optional.of(BigDecimal.TWO))))
        .isEqualTo(withRisk);
    assertThat(store.getSettings(withRisk))
        .hasValueSatisfying(
            stored -> assertThat(stored.riskPerTradePercent()).contains(BigDecimal.ONE));
  }

  @Test
  void findsNoSettingsForAnUnknownId() {
    assertThat(store.getSettings(Long.MAX_VALUE)).isEmpty();
  }

  private long count(String table) {
    return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
  }

  private static AlgorithmSettings settings(
      Optional<BigDecimal> riskPerTradePercent, Optional<BigDecimal> stopLossPercent) {
    return new AlgorithmSettings(
        SimpleMovingAverageAlgorithm.ID,
        20,
        CandleInterval.FIVE_MINUTES,
        false,
        Optional.of(30),
        new BigDecimal("100"),
        riskPerTradePercent,
        stopLossPercent,
        AlgorithmSettingValues.NONE);
  }
}
