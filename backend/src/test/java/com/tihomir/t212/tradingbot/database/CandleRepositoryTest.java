package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryRequest;
import com.tihomir.t212.tradingbot.marketdata.history.StoredCandleRange;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CandleRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final String BROKER = "binance";
  private static final String TICKER = "BTCUSDT";
  private static final CandleInterval INTERVAL = CandleInterval.FIVE_MINUTES;
  private static final Instant START = Instant.parse("2020-01-01T00:00:00Z");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired CandleRepository candleRepository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTables() {
    jdbcClient.sql("DELETE FROM candle").update();
    jdbcClient.sql("DELETE FROM ticker").update();
  }

  @Test
  void findsATickerOnlyAfterItIsInserted() {
    assertThat(candleRepository.getTickerId(BROKER, TICKER)).isEmpty();

    long tickerId = candleRepository.insertTicker(BROKER, TICKER);

    assertThat(candleRepository.getTickerId(BROKER, TICKER)).contains(tickerId);
    assertThat(candleRepository.insertTicker(BROKER, TICKER)).isEqualTo(tickerId);
    assertThat(candleRepository.getTickerId("other", TICKER)).isEmpty();
  }

  @Test
  void storesAPageAndReadsARangeAndTheStoredRangeBack() {
    long tickerId = candleRepository.insertTicker(BROKER, TICKER);

    candleRepository.insertCandlePage(tickerId, candles(0, 3));

    assertThat(candleRepository.getStoredCandleRange(tickerId, INTERVAL))
        .contains(storedRange(0, 3));
    assertThat(candleRepository.getStoredCandleRange(tickerId, CandleInterval.ONE_HOUR)).isEmpty();
    CandleList range = candleRepository.getCandles(request(1, 3));
    assertThat(range.candles()).containsExactly(candle(1), candle(2));
  }

  @Test
  void skipsStoredCandlesAndWidensTheStoredRange() {
    long tickerId = candleRepository.insertTicker(BROKER, TICKER);
    candleRepository.insertCandlePage(tickerId, candles(0, 3));

    candleRepository.insertCandlePage(tickerId, candles(2, 5));

    assertThat(candleRepository.getCandles(request(0, 10)).candles()).hasSize(6);
    assertThat(candleRepository.getStoredCandleRange(tickerId, INTERVAL))
        .contains(storedRange(0, 5));
  }

  @Test
  void aFailedPageStoresNoneOfItsCandles() {
    long tickerId = candleRepository.insertTicker(BROKER, TICKER);
    CandleUpdate withoutClose =
        new CandleUpdate(
            TICKER,
            INTERVAL,
            openTime(1),
            openTime(2),
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            null,
            BigDecimal.ONE,
            true,
            openTime(2));

    assertThatThrownBy(() ->
            candleRepository.insertCandlePage(
                tickerId, new CandleList(List.of(candle(0), withoutClose))))
        .isInstanceOf(DataAccessException.class);

    assertThat(candleRepository.getCandles(request(0, 10)).candles()).isEmpty();
    assertThat(candleRepository.getStoredCandleRange(tickerId, INTERVAL)).isEmpty();
  }

  private static CandleHistoryRequest request(int fromCandle, int toCandle) {
    return new CandleHistoryRequest(
        BROKER, TICKER, INTERVAL, openTime(fromCandle), openTime(toCandle));
  }

  private static StoredCandleRange storedRange(int firstCandle, int lastCandle) {
    return new StoredCandleRange(INTERVAL, openTime(firstCandle), openTime(lastCandle));
  }

  private static CandleList candles(int firstCandle, int lastCandle) {
    return new CandleList(
        IntStream.rangeClosed(firstCandle, lastCandle)
            .mapToObj(CandleRepositoryTest::candle)
            .toList());
  }

  private static CandleUpdate candle(int index) {
    BigDecimal price = new BigDecimal("7200.50000000").add(BigDecimal.valueOf(index));
    return new CandleUpdate(
        TICKER,
        INTERVAL,
        openTime(index),
        openTime(index + 1),
        price,
        price,
        price,
        price,
        new BigDecimal("12.34000000"),
        true,
        openTime(index + 1));
  }

  private static Instant openTime(int index) {
    return START.plus(INTERVAL.duration().multipliedBy(index));
  }
}
