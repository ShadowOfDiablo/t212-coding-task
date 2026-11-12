package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.bot.PortfolioPoint;
import com.tihomir.t212.tradingbot.bot.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.RecordedRun;
import com.tihomir.t212.tradingbot.bot.RunCounts;
import com.tihomir.t212.tradingbot.bot.RunEnd;
import com.tihomir.t212.tradingbot.bot.RunEvent;
import com.tihomir.t212.tradingbot.bot.RunSettingsChange;
import com.tihomir.t212.tradingbot.bot.RunStart;
import com.tihomir.t212.tradingbot.bot.RunStatus;
import com.tihomir.t212.tradingbot.bot.StoredRunDetails;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class AlgorithmRunRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final Instant STARTED_AT = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant CHANGED_AT = Instant.parse("2026-01-01T10:05:00Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-01-01T10:10:00Z");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired AlgorithmRunRepository runRepository;
  @Autowired SimpleMovingAverageSettingsStore settingsStore;
  @Autowired JdbcClient jdbcClient;

  private long firstSettingsId;
  private long secondSettingsId;

  @BeforeEach
  void emptyTablesAndStoreSettings() {
    jdbcClient.sql("DELETE FROM training_portfolio_point").update();
    jdbcClient.sql("DELETE FROM algorithm_run_settings").update();
    jdbcClient.sql("DELETE FROM algorithm_run").update();
    firstSettingsId = settingsStore.findOrInsertSettings(settings(20));
    secondSettingsId = settingsStore.findOrInsertSettings(settings(30));
  }

  @Test
  void recordsTheStartTheChangesAndTheEndOfARun() {
    long runId = runRepository.insertStartedRun(start("binance"));
    runRepository.insertSettingsChange(
        runId, new RunSettingsChange(RunEvent.CHANGED, CHANGED_AT, secondSettingsId));
    PortfolioPointList points =
        new PortfolioPointList(
            List.of(new PortfolioPoint(CHANGED_AT, new BigDecimal("500"), new BigDecimal("1010"))));

    runRepository.finishRun(runId, end(points));

    StoredRunDetails details = runRepository.getRun(runId).orElseThrow();
    assertThat(details.run().status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(details.run().finishedAt()).contains(FINISHED_AT);
    assertThat(details.run().finalValue()).hasValueSatisfying(this::isFinalValue);
    assertThat(details.run().counts()).isEqualTo(new RunCounts(3, 2, 1, 4));
    assertThat(details.settingsChanges().changes())
        .containsExactly(
            new RunSettingsChange(RunEvent.STARTED, STARTED_AT, firstSettingsId),
            new RunSettingsChange(RunEvent.CHANGED, CHANGED_AT, secondSettingsId),
            new RunSettingsChange(RunEvent.COMPLETED, FINISHED_AT, secondSettingsId));
    assertThat(details.points().points()).hasSize(1);
  }

  @Test
  void endsARunOnlyOnce() {
    long runId = runRepository.insertStartedRun(start("binance"));
    runRepository.finishRun(runId, end(PortfolioPointList.NONE));

    assertThatThrownBy(() -> runRepository.finishRun(runId, end(PortfolioPointList.NONE)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void stopsRunsLeftUnfinishedWithTheirLastSettings() {
    long unfinished = runRepository.insertStartedRun(start("binance"));
    runRepository.insertSettingsChange(
        unfinished, new RunSettingsChange(RunEvent.CHANGED, CHANGED_AT, secondSettingsId));
    long finished = runRepository.insertStartedRun(start("binance"));
    runRepository.finishRun(finished, end(PortfolioPointList.NONE));

    int stopped = runRepository.stopUnfinishedRuns(FINISHED_AT, "Interrupted by a restart");

    assertThat(stopped).isEqualTo(1);
    StoredRunDetails details = runRepository.getRun(unfinished).orElseThrow();
    assertThat(details.run().status()).isEqualTo(RunStatus.STOPPED);
    assertThat(details.run().failureReason()).contains("Interrupted by a restart");
    assertThat(details.settingsChanges().changes().getLast())
        .isEqualTo(new RunSettingsChange(RunEvent.COMPLETED, FINISHED_AT, secondSettingsId));
    assertThat(runRepository.getRun(finished).orElseThrow().run().status())
        .isEqualTo(RunStatus.COMPLETED);
  }

  @Test
  void listsTheRunsOfOneBrokerNewestFirst() {
    long older = runRepository.insertStartedRun(start("binance"));
    long newer = runRepository.insertStartedRun(start("binance"));
    runRepository.insertStartedRun(start("kraken"));

    assertThat(runRepository.getRuns("binance").runs())
        .extracting(RecordedRun::id)
        .containsExactly(newer, older);
    assertThat(runRepository.getRun(Long.MAX_VALUE)).isEmpty();
  }

  private void isFinalValue(BigDecimal finalValue) {
    assertThat(finalValue).isEqualByComparingTo("1010");
  }

  private RunStart start(String broker) {
    return new RunStart(
        BotMode.TRAINING,
        broker,
        "BTCUSDT",
        STARTED_AT,
        Optional.of(Instant.parse("2020-01-01T00:00:00Z")),
        Optional.of(Instant.parse("2020-02-01T00:00:00Z")),
        new BigDecimal("1000"),
        firstSettingsId);
  }

  private RunEnd end(PortfolioPointList points) {
    return new RunEnd(
        RunStatus.COMPLETED,
        FINISHED_AT,
        Optional.of(new BigDecimal("1010")),
        new RunCounts(3, 2, 1, 4),
        Optional.empty(),
        secondSettingsId,
        points);
  }

  private static AlgorithmSettings settings(int timePeriod) {
    return new AlgorithmSettings(
        SimpleMovingAverageAlgorithm.ID,
        timePeriod,
        CandleInterval.FIVE_MINUTES,
        true,
        Optional.empty(),
        new BigDecimal("100"),
        Optional.empty(),
        Optional.empty(),
        AlgorithmSettingValues.NONE);
  }
}
