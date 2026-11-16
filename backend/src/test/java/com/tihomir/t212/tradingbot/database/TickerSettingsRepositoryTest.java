package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.indicator.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.tickersettings.AssignedSettings;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import java.math.BigDecimal;
import java.time.Instant;
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
class TickerSettingsRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final String BROKER = "binance";
  private static final String TICKER = "SOLUSDT";
  private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");
  private static final BigDecimal FIRST_BUDGET = new BigDecimal("2500");
  private static final BigDecimal SECOND_BUDGET = new BigDecimal("2612.40");
  private static final AlgorithmSettings HOURLY = settings(CandleInterval.ONE_HOUR);
  private static final AlgorithmSettings DAILY = settings(CandleInterval.ONE_DAY);

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired TickerSettingsRepository repository;
  @Autowired AlgorithmRepository algorithmRepository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTable() {
    jdbcClient.sql("DELETE FROM active_algorithm").update();
  }

  @Test
  void storesAndReplacesATickersSettingsAndBudget() {
    long hourlyId = algorithmRepository.findOrInsertAlgorithm(HOURLY, CREATED);
    long dailyId = algorithmRepository.findOrInsertAlgorithm(DAILY, CREATED);

    repository.saveAssignedSettings(BROKER, TICKER, assigned(hourlyId, HOURLY, FIRST_BUDGET));
    repository.saveAssignedSettings(BROKER, TICKER, assigned(dailyId, DAILY, SECOND_BUDGET));

    assertThat(repository.getAssignedSettings(BROKER).assignedTo(TICKER))
        .hasValueSatisfying(stored -> {
          assertThat(stored.algorithmId()).isEqualTo(dailyId);
          assertThat(stored.settings()).isEqualTo(DAILY);
          assertThat(stored.budget())
              .hasValueSatisfying(budget -> assertThat(budget).isEqualByComparingTo(SECOND_BUDGET));
          assertThat(stored.source()).isEqualTo(SettingsSource.MANUAL);
          assertThat(stored.tunedRunId()).isEmpty();
        });
    assertThat(repository.getAssignedSettings("other").tickers()).isEmpty();
  }

  @Test
  void readsARowWrittenBeforeBudgetsAsPickedByHandWithoutABudget() {
    long hourlyId = algorithmRepository.findOrInsertAlgorithm(HOURLY, CREATED);
    repository.saveAssignedSettings(BROKER, TICKER, assigned(hourlyId, HOURLY, FIRST_BUDGET));
    jdbcClient.sql("UPDATE active_algorithm SET budget = NULL, settings_source = NULL").update();

    assertThat(repository.getAssignedSettings(BROKER).assignedTo(TICKER))
        .hasValueSatisfying(stored -> {
          assertThat(stored.budget()).isEmpty();
          assertThat(stored.source()).isEqualTo(SettingsSource.MANUAL);
        });
  }

  private static AssignedSettings assigned(
      long algorithmId, AlgorithmSettings settings, BigDecimal budget) {
    return new AssignedSettings(
        algorithmId, settings, Optional.of(budget), SettingsSource.MANUAL, Optional.empty());
  }

  private static AlgorithmSettings settings(CandleInterval timeframe) {
    return new SimpleMovingAverageAlgorithm()
        .resolveSettings(
            new AlgorithmSettingsRequest(
                AlgorithmType.SIMPLE_MOVING_AVERAGE, timeframe, true, Map.of()));
  }
}
