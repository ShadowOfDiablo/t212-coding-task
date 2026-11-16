package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.indicator.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import java.time.Instant;
import java.util.Map;
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
class SavedAlgorithmRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final Instant FIRST_SAVE = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant SECOND_SAVE = Instant.parse("2026-01-02T10:00:00Z");
  private static final String FIRST_NAME = "BTC hourly";
  private static final String SECOND_NAME = "ETH daily";
  private static final String NEW_NAME = "BTC hourly, tuned";
  private static final AlgorithmSettings HOURLY = settings(CandleInterval.ONE_HOUR);
  private static final AlgorithmSettings DAILY = settings(CandleInterval.ONE_DAY);

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired SavedAlgorithmRepository repository;
  @Autowired AlgorithmRepository algorithmRepository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTables() {
    jdbcClient.sql("DELETE FROM saved_algorithm").update();
  }

  @Test
  void listsSavedSettingsInSaveOrderWithTheirVariants() {
    long hourlyId = algorithmRepository.findOrInsertAlgorithm(HOURLY, FIRST_SAVE);
    long dailyId = algorithmRepository.findOrInsertAlgorithm(DAILY, FIRST_SAVE);

    long firstSavedId = repository.saveAlgorithm(hourlyId, FIRST_NAME, FIRST_SAVE);
    repository.saveAlgorithm(dailyId, SECOND_NAME, SECOND_SAVE);

    assertThat(repository.getSavedAlgorithms().savedAlgorithms())
        .extracting(SavedAlgorithm::name, SavedAlgorithm::algorithmId, SavedAlgorithm::settings)
        .containsExactly(tuple(FIRST_NAME, hourlyId, HOURLY), tuple(SECOND_NAME, dailyId, DAILY));
    assertThat(repository.getSavedAlgorithm(firstSavedId))
        .hasValueSatisfying(saved -> assertThat(saved.savedAt()).isEqualTo(FIRST_SAVE));
  }

  @Test
  void renamesAVariantSavedAgainInsteadOfAddingIt() {
    long hourlyId = algorithmRepository.findOrInsertAlgorithm(HOURLY, FIRST_SAVE);
    long savedId = repository.saveAlgorithm(hourlyId, FIRST_NAME, FIRST_SAVE);

    assertThat(repository.saveAlgorithm(hourlyId, NEW_NAME, SECOND_SAVE)).isEqualTo(savedId);

    assertThat(repository.getSavedAlgorithms().savedAlgorithms())
        .singleElement()
        .satisfies(saved -> {
          assertThat(saved.name()).isEqualTo(NEW_NAME);
          assertThat(saved.savedAt()).isEqualTo(FIRST_SAVE);
        });
  }

  @Test
  void pointsAnEntryAtAnotherVariantKeepingItsNameAndPlace() {
    long hourlyId = algorithmRepository.findOrInsertAlgorithm(HOURLY, FIRST_SAVE);
    long dailyId = algorithmRepository.findOrInsertAlgorithm(DAILY, FIRST_SAVE);
    long savedId = repository.saveAlgorithm(hourlyId, FIRST_NAME, FIRST_SAVE);

    assertThat(repository.replaceAlgorithm(savedId, dailyId)).isTrue();
    assertThat(repository.replaceAlgorithm(Long.MAX_VALUE, dailyId)).isFalse();

    assertThat(repository.getSavedAlgorithm(savedId))
        .hasValueSatisfying(saved -> {
          assertThat(saved.name()).isEqualTo(FIRST_NAME);
          assertThat(saved.savedAt()).isEqualTo(FIRST_SAVE);
          assertThat(saved.algorithmId()).isEqualTo(dailyId);
          assertThat(saved.settings()).isEqualTo(DAILY);
        });
  }

  @Test
  void keepsTheNumbersOfTheOtherEntriesWhenOneIsDeleted() {
    long first = saveVariant(CandleInterval.ONE_HOUR, FIRST_NAME);
    long second = saveVariant(CandleInterval.ONE_DAY, SECOND_NAME);
    long third = saveVariant(CandleInterval.FOUR_HOURS, NEW_NAME);

    repository.deleteSavedAlgorithm(second);
    long fourth = saveVariant(CandleInterval.FIFTEEN_MINUTES, FIRST_NAME);

    assertThat(repository.getSavedAlgorithms().savedAlgorithms())
        .extracting(SavedAlgorithm::id, SavedAlgorithm::number)
        .containsExactly(tuple(first, 1), tuple(third, 3), tuple(fourth, 4));
  }

  @Test
  void deletesTheEntryButKeepsTheVariant() {
    long hourlyId = algorithmRepository.findOrInsertAlgorithm(HOURLY, FIRST_SAVE);
    long savedId = repository.saveAlgorithm(hourlyId, FIRST_NAME, FIRST_SAVE);

    assertThat(repository.deleteSavedAlgorithm(savedId)).isTrue();
    assertThat(repository.deleteSavedAlgorithm(savedId)).isFalse();

    assertThat(repository.getSavedAlgorithms().savedAlgorithms()).isEmpty();
    assertThat(repository.getSavedAlgorithm(savedId)).isEmpty();
    assertThat(algorithmRepository.getAlgorithm(hourlyId)).contains(HOURLY);
  }

  private long saveVariant(CandleInterval timeframe, String name) {
    long algorithmId = algorithmRepository.findOrInsertAlgorithm(settings(timeframe), FIRST_SAVE);
    return repository.saveAlgorithm(algorithmId, name, FIRST_SAVE);
  }

  private static AlgorithmSettings settings(CandleInterval timeframe) {
    return new SimpleMovingAverageAlgorithm()
        .resolveSettings(
            new AlgorithmSettingsRequest(
                AlgorithmType.SIMPLE_MOVING_AVERAGE, timeframe, true, Map.of()));
  }
}
