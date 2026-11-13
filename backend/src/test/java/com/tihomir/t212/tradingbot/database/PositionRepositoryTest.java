package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.bot.RunStart;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import com.tihomir.t212.tradingbot.position.OpenLot;
import com.tihomir.t212.tradingbot.position.OpenLotList;
import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import com.tihomir.t212.tradingbot.position.StoredPosition;
import com.tihomir.t212.tradingbot.position.Trade;
import com.tihomir.t212.tradingbot.position.TradeList;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired PositionRepository repository;
  @Autowired AlgorithmRunRepository runRepository;
  @Autowired AlgorithmRepository algorithmRepository;
  @Autowired JdbcClient jdbcClient;

  private long liveRunId;
  private long trainingRunId;

  @BeforeEach
  void emptyTablesAndStartRuns() {
    jdbcClient.sql("DELETE FROM trade").update();
    jdbcClient.sql("DELETE FROM open_lot").update();
    jdbcClient.sql("DELETE FROM position").update();
    jdbcClient.sql("DELETE FROM active_algorithm").update();
    jdbcClient.sql("DELETE FROM training_portfolio_point").update();
    jdbcClient.sql("DELETE FROM algorithm_run").update();
    long algorithmId = algorithmRepository.findOrInsertAlgorithm(settings(), FIRST_BUY);
    liveRunId = runRepository.insertStartedRun(start(BotMode.TRADING, algorithmId));
    trainingRunId = runRepository.insertStartedRun(start(BotMode.TRAINING, algorithmId));
  }

  @Test
  void updatesThePositionOnEveryWriteAndReplacesItsLots() {
    repository.savePosition(openWrite(liveRunId, List.of(lot(liveRunId, FIRST_BUY))));
    repository.savePosition(
        openWrite(liveRunId, List.of(lot(liveRunId, FIRST_BUY), lot(liveRunId, SECOND_BUY))));

    assertThat(count("position")).isEqualTo(1);
    assertThat(repository.findOpenLivePosition(BROKER, TICKER))
        .hasValueSatisfying(open -> {
          assertThat(open.position().runId()).isEqualTo(liveRunId);
          assertThat(open.openLots().lots())
              .extracting(OpenLot::boughtAt)
              .containsExactly(FIRST_BUY, SECOND_BUY);
        });
  }

  @Test
  void closesThePositionAddsItsTradesAndListsTheWorstFirst() {
    repository.savePosition(openWrite(liveRunId, List.of(lot(liveRunId, FIRST_BUY))));
    repository.savePosition(closedWrite(liveRunId, FIRST_BUY, "-5"));
    repository.savePosition(closedWrite(liveRunId, SECOND_BUY, "8"));

    assertThat(count("open_lot")).isZero();
    assertThat(repository.findOpenLivePosition(BROKER, TICKER)).isEmpty();
    assertThat(repository.getTrades(liveRunId).trades())
        .extracting(Trade::realisedPnl)
        .containsExactly(new BigDecimal("-5"), new BigDecimal("8"));
    assertThat(repository.getWorstPositions(liveRunId, 1).positions())
        .singleElement()
        .satisfies(stored -> {
          assertThat(stored.position().status()).isEqualTo(PositionStatus.CLOSED);
          assertThat(stored.position().closedAt()).contains(SELL);
          assertThat(stored.position().realisedPnl()).isEqualByComparingTo("-5");
        });
    assertThat(repository.getWorstPositions(liveRunId, 10).positions())
        .extracting(StoredPosition::position)
        .extracting(Position::openedAt)
        .containsExactly(FIRST_BUY, SECOND_BUY);
  }

  @Test
  void restoresOnlyOpenPositionsOfLiveRuns() {
    repository.savePosition(openWrite(trainingRunId, List.of(lot(trainingRunId, FIRST_BUY))));

    assertThat(repository.findOpenLivePosition(BROKER, TICKER)).isEmpty();
    assertThat(repository.findOpenLivePosition(BROKER, "ETHUSDT")).isEmpty();
  }

  @Test
  void writesNothingWhenAPartOfTheWriteFails() {
    PositionWrite brokenWrite =
        new PositionWrite(
            position(liveRunId, FIRST_BUY, PositionStatus.OPEN, "0"),
            new OpenLotList(List.of(lot(MISSING_RUN_ID, FIRST_BUY))),
            TradeList.NONE);

    assertThatThrownBy(() -> repository.savePosition(brokenWrite))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("position")).isZero();
  }

  private long count(String table) {
    return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
  }

  private static PositionWrite openWrite(long runId, List<OpenLot> lots) {
    return new PositionWrite(
        position(runId, FIRST_BUY, PositionStatus.OPEN, "0"),
        new OpenLotList(lots),
        TradeList.NONE);
  }

  private static PositionWrite closedWrite(long runId, Instant openedAt, String realisedPnl) {
    Trade trade =
        new Trade(
            runId,
            openedAt,
            SELL,
            BigDecimal.ONE,
            new BigDecimal("100"),
            new BigDecimal("100"),
            BigDecimal.ZERO,
            new BigDecimal(realisedPnl));
    return new PositionWrite(
        position(runId, openedAt, PositionStatus.CLOSED, realisedPnl),
        OpenLotList.NONE,
        new TradeList(List.of(trade)));
  }

  private static Position position(
      long runId, Instant openedAt, PositionStatus status, String realisedPnl) {
    Optional<Instant> closedAt = Optional.empty();
    if (status == PositionStatus.CLOSED) {
      closedAt = Optional.of(SELL);
    }
    return new Position(
        runId,
        BROKER,
        TICKER,
        status,
        openedAt,
        closedAt,
        new BigDecimal("100"),
        new BigDecimal(realisedPnl),
        new BigDecimal("-3"),
        new BigDecimal("4"));
  }

  private static OpenLot lot(long runId, Instant boughtAt) {
    return new OpenLot(runId, boughtAt, BigDecimal.ONE, new BigDecimal("100"));
  }

  private static RunStart start(BotMode mode, long algorithmId) {
    return new RunStart(
        mode,
        BROKER,
        TICKER,
        FIRST_BUY,
        Optional.empty(),
        Optional.empty(),
        new BigDecimal("1000"),
        algorithmId);
  }

  private static AlgorithmSettings settings() {
    return new AlgorithmSettings(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        20,
        CandleInterval.FIVE_MINUTES,
        true,
        Optional.empty(),
        new BigDecimal("100"),
        Optional.empty(),
        Optional.empty(),
        AlgorithmSettingValues.NONE);
  }
}
