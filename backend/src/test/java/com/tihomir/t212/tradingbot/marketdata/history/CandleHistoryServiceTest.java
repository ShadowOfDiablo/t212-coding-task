package com.tihomir.t212.tradingbot.marketdata.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.order.TickerNotTradedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class CandleHistoryServiceTest {

  private static final String BROKER = "binance";
  private static final String TICKER = FakeBroker.TRADED_TICKER;
  private static final CandleInterval INTERVAL = CandleInterval.ONE_SECOND;
  private static final Instant NOW = Instant.ofEpochSecond(100);
  private static final long STORED_TICKER_ID = 1;

  private final RestClient.Builder restBuilder = RestClient.builder();
  private final MockRestServiceServer rest = MockRestServiceServer.bindTo(restBuilder).build();
  private final FakeCandleStore candleStore = new FakeCandleStore();

  @Test
  void downloadsMissingCandlesPageByPageAndStoresEachPage() {
    expectPage(0, "0,1");
    expectPage(2, "2,3");
    expectPage(4, "4");

    CandleList candles = service(NOW).getCandles(request(0, 5));

    assertThat(openSeconds(candles)).containsExactly(0L, 1L, 2L, 3L, 4L);
    assertThat(candleStore.insertedPages()).isEqualTo(3);
    assertThat(candleStore.storedRange(INTERVAL)).contains(storedRange(0, 4));
    rest.verify();
  }

  @Test
  void servesAStoredRangeWithoutCallingTheExchange() {
    candleStore.storeCandles(STORED_TICKER_ID, candles(0, 9));

    CandleList candles = service(NOW).getCandles(request(2, 5));

    assertThat(openSeconds(candles)).containsExactly(2L, 3L, 4L);
    assertThat(candleStore.insertedPages()).isZero();
    rest.verify();
  }

  @Test
  void downloadsOnlyTheMissingTail() {
    candleStore.storeCandles(STORED_TICKER_ID, candles(0, 3));
    expectPage(4, "4,5");

    CandleList candles = service(NOW).getCandles(request(0, 6));

    assertThat(openSeconds(candles)).containsExactly(0L, 1L, 2L, 3L, 4L, 5L);
    assertThat(candleStore.storedRange(INTERVAL)).contains(storedRange(0, 5));
    rest.verify();
  }

  @Test
  void downloadsAnEarlierRangeBackwardFromTheFirstStoredCandle() {
    candleStore.storeCandles(STORED_TICKER_ID, candles(4, 5));
    expectPage(2, "2,3");
    expectPage(0, "0,1");

    CandleList candles = service(NOW).getCandles(request(0, 6));

    assertThat(openSeconds(candles)).containsExactly(0L, 1L, 2L, 3L, 4L, 5L);
    assertThat(candleStore.storedRange(INTERVAL)).contains(storedRange(0, 5));
    rest.verify();
  }

  @Test
  void stopsPagingBackwardWhenTheExchangeHasNoOlderCandles() {
    candleStore.storeCandles(STORED_TICKER_ID, candles(4, 5));
    expectPage(2, "2,3");
    expectPage(0, "");

    CandleList candles = service(NOW).getCandles(request(0, 6));

    assertThat(openSeconds(candles)).containsExactly(2L, 3L, 4L, 5L);
    assertThat(candleStore.storedRange(INTERVAL)).contains(storedRange(2, 5));
    rest.verify();
  }

  @Test
  void keepsTheStoredBlockWhenABackwardDownloadFails() {
    candleStore.storeCandles(STORED_TICKER_ID, candles(4, 5));
    expectPage(2, "2,3");
    rest.expect(requestTo(pageUri(0))).andRespond(withServerError());

    assertThatThrownBy(() -> service(NOW).getCandles(request(0, 6)))
        .isInstanceOf(CandleDownloadFailedException.class);

    assertThat(candleStore.storedRange(INTERVAL)).contains(storedRange(2, 5));
    rest.verify();
  }

  @Test
  void keepsTheStoredPagesWhenADownloadFailsAndContinuesFromThemLater() {
    expectPage(0, "0,1");
    rest.expect(requestTo(pageUri(2))).andRespond(withServerError());

    assertThatThrownBy(() -> service(NOW).getCandles(request(0, 5)))
        .isInstanceOf(CandleDownloadFailedException.class);
    assertThat(candleStore.storedRange(INTERVAL)).contains(storedRange(0, 1));
    rest.verify();

    rest.reset();
    expectPage(2, "2,3");
    expectPage(4, "4");
    CandleList candles = service(NOW).getCandles(request(0, 5));

    assertThat(openSeconds(candles)).containsExactly(0L, 1L, 2L, 3L, 4L);
    rest.verify();
  }

  @Test
  void reportsAnUnreadablePageAsAFailedDownload() {
    rest.expect(requestTo(pageUri(0))).andRespond(text("not-a-candle"));

    assertThatThrownBy(() -> service(NOW).getCandles(request(0, 5)))
        .isInstanceOf(CandleDownloadFailedException.class);
    assertThat(candleStore.storedRange(INTERVAL)).isEmpty();
  }

  @Test
  void stopsWithoutStoringAnythingWhenTheExchangeHasNoCandles() {
    expectPage(0, "");

    CandleList candles = service(NOW).getCandles(request(0, 5));

    assertThat(candles.candles()).isEmpty();
    assertThat(candleStore.storedRange(INTERVAL)).isEmpty();
    rest.verify();
  }

  @Test
  void neverRequestsTheCandleThatIsStillForming() {
    expectPage(0, "0,1");
    expectPage(2, "2");

    CandleList candles = service(Instant.ofEpochMilli(3_500)).getCandles(request(0, 10));

    assertThat(openSeconds(candles)).containsExactly(0L, 1L, 2L);
    rest.verify();
  }

  @Test
  void returnsNothingForARangeThatHasNotClosedYet() {
    CandleList candles = service(NOW).getCandles(request(100, 200));

    assertThat(candles.candles()).isEmpty();
    rest.verify();
  }

  @Test
  void refusesARangeWithTooManyCandles() {
    long tooManySeconds = CandleHistoryService.MAX_CANDLES_PER_TRAINING_RUN + 1;

    assertThatThrownBy(() -> service(NOW).getCandles(request(0, tooManySeconds)))
        .isInstanceOf(CandleRangeTooLargeException.class);
    rest.verify();
  }

  @Test
  void refusesAnUnknownBrokerOrATickerTheBrokerDoesNotTrade() {
    assertThatThrownBy(() ->
            service(NOW)
                .getCandles(
                    new CandleHistoryRequest(
                        "kraken",
                        TICKER,
                        INTERVAL,
                        Instant.ofEpochSecond(0),
                        Instant.ofEpochSecond(1))))
        .isInstanceOf(BrokerNotFoundException.class);
    assertThatThrownBy(() ->
            service(NOW)
                .getCandles(
                    new CandleHistoryRequest(
                        BROKER,
                        "DOGEUSDT",
                        INTERVAL,
                        Instant.ofEpochSecond(0),
                        Instant.ofEpochSecond(1))))
        .isInstanceOf(TickerNotTradedException.class);
  }

  @Test
  void passesOnADatabaseFailure() {
    candleStore.takeDatabaseDown();

    assertThatThrownBy(() -> service(NOW).getCandles(request(0, 5)))
        .isInstanceOf(DataAccessException.class);
  }

  private CandleHistoryService service(Instant now) {
    FakeBroker broker = new FakeBroker(BROKER, restBuilder.build());
    return new CandleHistoryService(
        new BrokerRegistryService(List.of(broker)), candleStore, Clock.fixed(now, ZoneOffset.UTC));
  }

  private static CandleHistoryRequest request(long fromSecond, long toSecond) {
    return new CandleHistoryRequest(
        BROKER,
        TICKER,
        INTERVAL,
        Instant.ofEpochSecond(fromSecond),
        Instant.ofEpochSecond(toSecond));
  }

  private static StoredCandleRange storedRange(long firstSecond, long lastSecond) {
    return new StoredCandleRange(
        INTERVAL, Instant.ofEpochSecond(firstSecond), Instant.ofEpochSecond(lastSecond));
  }

  private static List<CandleUpdate> candles(long firstSecond, long lastSecond) {
    return LongStream.rangeClosed(firstSecond, lastSecond)
        .mapToObj(CandleHistoryServiceTest::candle)
        .toList();
  }

  private static CandleUpdate candle(long openSecond) {
    BigDecimal price = BigDecimal.valueOf(openSecond);
    return new CandleUpdate(
        TICKER,
        INTERVAL,
        Instant.ofEpochSecond(openSecond),
        Instant.ofEpochSecond(openSecond + 1),
        price,
        price,
        price,
        price,
        BigDecimal.ZERO,
        true,
        Instant.ofEpochSecond(openSecond + 1));
  }

  private static List<Long> openSeconds(CandleList candles) {
    return candles.candles().stream().map(candle -> candle.openTime().getEpochSecond()).toList();
  }

  private void expectPage(long fromSecond, String openSeconds) {
    rest.expect(requestTo(pageUri(fromSecond))).andRespond(text(openSeconds));
  }

  private static String pageUri(long fromSecond) {
    return "http://rest.test/candles?from=" + fromSecond;
  }

  private static ResponseCreator text(String body) {
    return withSuccess(body, MediaType.TEXT_PLAIN);
  }
}
