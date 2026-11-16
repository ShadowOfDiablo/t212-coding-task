package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.bot.run.RunStart;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import com.tihomir.t212.tradingbot.position.StoredPosition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PositionRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final String BROKER = "binance";
  private static final String TICKER = "BTCUSDT";
  private static final Instant FIRST_BUY = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant SECOND_BUY = Instant.parse("2026-01-01T11:00:00Z");
  private static final Instant SELL = Instant.parse("2026-01-01T12:00:00Z");
  private static final long MISSING_RUN_ID = Long.MAX_VALUE;
  private static final int ONE_POSITION = 1;
  private static final int ALL_POSITIONS = 10;
  private static final String NO_PNL = "0";
  private static final String LATER_PNL = "2";
  private static final String LOSS = "-5";
  private static final String PROFIT = "8";
  private static final BigDecimal COST = new BigDecimal("100");
  private static final BigDecimal MAE = new BigDecimal("-3");
  private static final BigDecimal MFE = new BigDecimal("4");
  private static final BigDecimal STARTING_VALUE = new BigDecimal("1000");
  private static final int TIME_PERIOD = 20;
  private static final BigDecimal ORDER_SIZE_PERCENT = new BigDecimal("100");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired PositionRepository repository;
  @Autowired AlgorithmRunRepository runRepository;
  @Autowired AlgorithmRepository algorithmRepository;
  @Autowired JdbcClient jdbcClient;

  private long runId;

  @BeforeEach
  void emptyTablesAndStartARun() {
    jdbcClient.sql("DELETE FROM position").update();
    jdbcClient.sql("DELETE FROM active_algorithm").update();
    jdbcClient.sql("DELETE FROM training_portfolio_point").update();
    jdbcClient.sql("DELETE FROM algorithm_run").update();
    long algorithmId = algorithmRepository.findOrInsertAlgorithm(settings(), FIRST_BUY);
    runId = runRepository.insertStartedRun(start(algorithmId));
  }

  @Test
  void updatesThePositionOnEveryWrite() {
    repository.savePosition(write(runId, FIRST_BUY, PositionStatus.OPEN, NO_PNL));
    repository.savePosition(write(runId, FIRST_BUY, PositionStatus.OPEN, LATER_PNL));

    assertThat(count("position")).isEqualTo(ONE_POSITION);
    assertThat(jdbcClient.sql("SELECT realised_pnl FROM position").query(BigDecimal.class).single())
        .isEqualByComparingTo(LATER_PNL);
  }

  @Test
  void closesThePositionAndListsTheWorstFirst() {
    repository.savePosition(write(runId, FIRST_BUY, PositionStatus.OPEN, NO_PNL));
    repository.savePosition(write(runId, FIRST_BUY, PositionStatus.CLOSED, LOSS));
    repository.savePosition(write(runId, SECOND_BUY, PositionStatus.CLOSED, PROFIT));

    assertThat(repository.getWorstPositions(runId, ONE_POSITION).positions())
        .singleElement()
        .satisfies(stored -> {
          assertThat(stored.position().broker()).isEqualTo(BROKER);
          assertThat(stored.position().ticker()).isEqualTo(TICKER);
          assertThat(stored.position().status()).isEqualTo(PositionStatus.CLOSED);
          assertThat(stored.position().closedAt()).contains(SELL);
          assertThat(stored.position().realisedPnl()).isEqualByComparingTo(LOSS);
        });
    assertThat(repository.getWorstPositions(runId, ALL_POSITIONS).positions())
        .extracting(StoredPosition::position)
        .extracting(Position::openedAt)
        .containsExactly(FIRST_BUY, SECOND_BUY);
  }

  @Test
  void listsEveryPositionOfTheRunNewestFirst() {
    repository.savePosition(write(runId, FIRST_BUY, PositionStatus.CLOSED, LOSS));
    repository.savePosition(write(runId, SECOND_BUY, PositionStatus.OPEN, NO_PNL));

    assertThat(repository.getPositions(runId).positions())
        .extracting(StoredPosition::position)
        .extracting(Position::openedAt, Position::status)
        .containsExactly(
            tuple(SECOND_BUY, PositionStatus.OPEN), tuple(FIRST_BUY, PositionStatus.CLOSED));
    assertThat(repository.getPositions(MISSING_RUN_ID).positions()).isEmpty();
  }

  @Test
  void refusesAPositionOfAMissingRun() {
    assertThatThrownBy(() ->
            repository.savePosition(write(MISSING_RUN_ID, FIRST_BUY, PositionStatus.OPEN, NO_PNL)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("position")).isZero();
  }

  private long count(String table) {
    return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
  }

  private static PositionWrite write(
      long runId, Instant openedAt, PositionStatus status, String realisedPnl) {
    Optional<Instant> closedAt = Optional.empty();
    if (status == PositionStatus.CLOSED) {
      closedAt = Optional.of(SELL);
    }
    return new PositionWrite(
        new Position(
            runId,
            BROKER,
            TICKER,
            status,
            openedAt,
            closedAt,
            COST,
            new BigDecimal(realisedPnl),
            MAE,
            MFE));
  }

  private static RunStart start(long algorithmId) {
    return new RunStart(
        BotMode.TRADING,
        BROKER,
        TICKER,
        FIRST_BUY,
        Optional.empty(),
        Optional.empty(),
        STARTING_VALUE,
        algorithmId,
        SettingsSource.MANUAL);
  }

  private static AlgorithmSettings settings() {
    return new AlgorithmSettings(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        TIME_PERIOD,
        CandleInterval.FIVE_MINUTES,
        true,
        Optional.empty(),
        ORDER_SIZE_PERCENT,
        Optional.empty(),
        Optional.empty(),
        AlgorithmSettingValues.NONE);
  }
}
