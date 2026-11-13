package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.bot.PortfolioPoint;
import com.tihomir.t212.tradingbot.bot.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.RecordedRun;
import com.tihomir.t212.tradingbot.bot.RunCounts;
import com.tihomir.t212.tradingbot.bot.RunEnd;
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
  private static final Instant POINT_TIME = Instant.parse("2026-01-01T10:05:00Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-01-01T10:10:00Z");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired AlgorithmRunRepository runRepository;
  @Autowired AlgorithmRepository algorithmRepository;
  @Autowired JdbcClient jdbcClient;

  private long firstAlgorithmId;
  private long secondAlgorithmId;

  @BeforeEach
  void emptyTablesAndStoreSettings() {
    jdbcClient.sql("DELETE FROM training_portfolio_point").update();
    jdbcClient.sql("DELETE FROM active_algorithm").update();
    jdbcClient.sql("DELETE FROM algorithm_run").update();
    firstAlgorithmId = algorithmRepository.findOrInsertAlgorithm(settings(20), STARTED_AT);
    secondAlgorithmId = algorithmRepository.findOrInsertAlgorithm(settings(30), STARTED_AT);
  }

  @Test
  void recordsTheStartAndTheEndOfARun() {
    long runId =
        runRepository.insertStartedRun(start(BotMode.TRAINING, "binance", firstAlgorithmId));
    PortfolioPointList points =
        new PortfolioPointList(
            List.of(new PortfolioPoint(POINT_TIME, new BigDecimal("500"), new BigDecimal("1010"))));

    runRepository.finishRun(runId, end(points));

    StoredRunDetails details = runRepository.getRun(runId).orElseThrow();
    assertThat(details.run().status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(details.run().finishedAt()).contains(FINISHED_AT);
    assertThat(details.run().finalValue()).hasValueSatisfying(this::isFinalValue);
    assertThat(details.run().counts()).isEqualTo(new RunCounts(3, 2, 1, 4));
    assertThat(details.run().algorithmId()).isEqualTo(firstAlgorithmId);
    assertThat(activeAlgorithmCount()).isZero();
    assertThat(details.points().points()).hasSize(1);
  }

  @Test
  void endsARunOnlyOnce() {
    long runId =
        runRepository.insertStartedRun(start(BotMode.TRAINING, "binance", firstAlgorithmId));
    runRepository.finishRun(runId, end(PortfolioPointList.NONE));

    assertThatThrownBy(() -> runRepository.finishRun(runId, end(PortfolioPointList.NONE)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void stopsRunsLeftUnfinished() {
    long unfinished =
        runRepository.insertStartedRun(start(BotMode.TRAINING, "binance", firstAlgorithmId));
    long finished =
        runRepository.insertStartedRun(start(BotMode.TRAINING, "binance", firstAlgorithmId));
    runRepository.finishRun(finished, end(PortfolioPointList.NONE));

    int stopped = runRepository.stopUnfinishedRuns(FINISHED_AT, "Interrupted by a restart");

    assertThat(stopped).isEqualTo(1);
    StoredRunDetails details = runRepository.getRun(unfinished).orElseThrow();
    assertThat(details.run().status()).isEqualTo(RunStatus.STOPPED);
    assertThat(details.run().failureReason()).contains("Interrupted by a restart");
    assertThat(runRepository.getRun(finished).orElseThrow().run().status())
        .isEqualTo(RunStatus.COMPLETED);
  }

  @Test
  void listsTheRunsOfOneBrokerNewestFirst() {
    long older =
        runRepository.insertStartedRun(start(BotMode.TRAINING, "binance", firstAlgorithmId));
    long newer =
        runRepository.insertStartedRun(start(BotMode.TRAINING, "binance", firstAlgorithmId));
    runRepository.insertStartedRun(start(BotMode.TRAINING, "kraken", firstAlgorithmId));

    assertThat(runRepository.getRuns("binance").runs())
        .extracting(RecordedRun::id)
        .containsExactly(newer, older);
    assertThat(runRepository.getRun(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  void replacesALiveRunAndKeepsTheActiveAlgorithmOfItsTicker() {
    long firstRunId =
        runRepository.insertStartedRun(start(BotMode.TRADING, "binance", firstAlgorithmId));
    assertThat(activeAlgorithmId()).isEqualTo(firstAlgorithmId);

    long nextRunId =
        runRepository.replaceRun(
            firstRunId,
            end(PortfolioPointList.NONE),
            start(BotMode.TRADING, "binance", secondAlgorithmId));

    assertThat(runRepository.getRun(firstRunId).orElseThrow().run().status())
        .isEqualTo(RunStatus.COMPLETED);
    RecordedRun nextRun = runRepository.getRun(nextRunId).orElseThrow().run();
    assertThat(nextRun.status()).isEqualTo(RunStatus.STARTED);
    assertThat(nextRun.algorithmId()).isEqualTo(secondAlgorithmId);
    assertThat(activeAlgorithmCount()).isEqualTo(1);
    assertThat(activeAlgorithmId()).isEqualTo(secondAlgorithmId);
  }

  @Test
  void replacesNothingWhenTheRunIsNotRunning() {
    long runId =
        runRepository.insertStartedRun(start(BotMode.TRADING, "binance", firstAlgorithmId));
    runRepository.finishRun(runId, end(PortfolioPointList.NONE));

    assertThatThrownBy(() ->
            runRepository.replaceRun(
                runId,
                end(PortfolioPointList.NONE),
                start(BotMode.TRADING, "binance", secondAlgorithmId)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(runRepository.getRuns("binance").runs()).hasSize(1);
    assertThat(activeAlgorithmId()).isEqualTo(firstAlgorithmId);
  }

  private long activeAlgorithmCount() {
    return jdbcClient.sql("SELECT count(*) FROM active_algorithm").query(Long.class).single();
  }

  private long activeAlgorithmId() {
    return jdbcClient
        .sql("SELECT algorithm_id FROM active_algorithm WHERE broker = ? AND ticker = ?")
        .param("binance")
        .param("BTCUSDT")
        .query(Long.class)
        .single();
  }

  private void isFinalValue(BigDecimal finalValue) {
    assertThat(finalValue).isEqualByComparingTo("1010");
  }

  private static RunStart start(BotMode mode, String broker, long algorithmId) {
    return new RunStart(
        mode,
        broker,
        "BTCUSDT",
        STARTED_AT,
        Optional.of(Instant.parse("2020-01-01T00:00:00Z")),
        Optional.of(Instant.parse("2020-02-01T00:00:00Z")),
        new BigDecimal("1000"),
        algorithmId);
  }

  private static RunEnd end(PortfolioPointList points) {
    return new RunEnd(
        RunStatus.COMPLETED,
        FINISHED_AT,
        Optional.of(new BigDecimal("1010")),
        new RunCounts(3, 2, 1, 4),
        Optional.empty(),
        points);
  }

  private static AlgorithmSettings settings(int timePeriod) {
    return new AlgorithmSettings(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
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
