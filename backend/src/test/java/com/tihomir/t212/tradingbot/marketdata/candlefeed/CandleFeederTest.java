package com.tihomir.t212.tradingbot.marketdata.candlefeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceMap;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuote;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class CandleFeederTest {

  private static final Instant RECEIVED = Instant.parse("2026-11-10T10:00:01Z");
  private static final Duration EXCHANGE_CLOCK_AHEAD = Duration.ofSeconds(10);
  private static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(1);
  private static final int STREAMS_PER_TICKER = 2;
  private static final int TICKERS_PER_CONNECTION =
      FakeExchangeMarketDataProtocol.MAX_STREAMS / STREAMS_PER_TICKER;

  private final FakeMarketDataStreamConnectionFactory connections =
      new FakeMarketDataStreamConnectionFactory();
  private final RestClient.Builder restBuilder = RestClient.builder();
  private final MockRestServiceServer rest = MockRestServiceServer.bindTo(restBuilder).build();
  private CandleFeeder feeder;

  @AfterEach
  void stopFeeder() {
    if (feeder != null) {
      feeder.close();
    }
  }

  @Test
  void groupsStreamsByConnectionWithoutSplittingATicker() {
    int tickerCount = 2 * TICKERS_PER_CONNECTION + 276;
    List<String> tickers = tickers(tickerCount);
    feeder = feeder(tickers, new TickerPriceMap(tickers));

    List<List<String>> streamsPerConnection =
        feeder.connectionUris().stream().map(CandleFeederTest::streamsOf).toList();

    assertThat(streamsPerConnection).hasSize(3);
    assertThat(streamsPerConnection)
        .allMatch(streams -> streams.size() <= FakeExchangeMarketDataProtocol.MAX_STREAMS);
    assertThat(streamsPerConnection.get(2)).hasSize(276 * STREAMS_PER_TICKER);
    Set<String> allStreams = new HashSet<>();
    streamsPerConnection.forEach(allStreams::addAll);
    assertThat(allStreams).hasSize(tickerCount * STREAMS_PER_TICKER);
    String lastTickerOfFirstConnection = "t" + (TICKERS_PER_CONNECTION - 1);
    assertThat(streamsPerConnection.get(0))
        .contains(lastTickerOfFirstConnection + "@candle", lastTickerOfFirstConnection + "@book")
        .doesNotContain("t" + TICKERS_PER_CONNECTION + "@candle");
  }

  @Test
  void opensOneConnectionPerStreamGroupAndClosesThemOnStop() {
    List<String> tickers = tickers(TICKERS_PER_CONNECTION + 1);
    feeder = feeder(tickers, new TickerPriceMap(tickers));
    expectServerTime(Instant.now());

    feeder.start();
    assertThat(connections.opened()).hasSize(2).allMatch(FakeMarketDataStreamConnection::isOpen);

    feeder.stop();
    assertThat(connections.opened()).allMatch(connection -> !connection.isOpen());
  }

  @Test
  void addingATickerWhileRunningReopensTheConnectionsWithItsStreams() {
    List<String> tickers = tickers(1);
    feeder = feeder(tickers, new TickerPriceMap(tickers));
    expectServerTime(Instant.now());
    feeder.start();

    feeder.addTicker("new");
    feeder.addTicker("new");

    assertThat(connections.opened()).hasSize(2);
    assertThat(connections.opened().getFirst().isOpen()).isFalse();
    assertThat(connections.opened().getLast().isOpen()).isTrue();
    assertThat(feeder.connectionUris()).singleElement().satisfies(this::streamsNewTicker);
  }

  private void streamsNewTicker(URI uri) {
    assertThat(streamsOf(uri)).contains("new@candle", "new@book");
  }

  @Test
  void startIsAllOrNothingWhenAConnectionFails() {
    List<String> tickers = tickers(2 * TICKERS_PER_CONNECTION + 1);
    feeder = feeder(tickers, new TickerPriceMap(tickers));
    connections.failOnOpenNumber(3);
    expectServerTime(Instant.now());

    assertThatThrownBy(feeder::start).isInstanceOf(IllegalStateException.class);

    assertThat(connections.opened()).hasSize(2).allMatch(connection -> !connection.isOpen());
  }

  @Test
  void stampsBidAskUpdatesWithTheExchangeClock() {
    TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT"));
    feeder = feeder(List.of("BTCUSDT"), prices);
    expectServerTime(Instant.now().plus(EXCHANGE_CLOCK_AHEAD));
    feeder.start();

    connections.opened().getFirst().receive("B|BTCUSDT|100.1|100.2", RECEIVED);

    Instant expected = RECEIVED.plus(EXCHANGE_CLOCK_AHEAD);
    assertThat(prices.get("BTCUSDT").orElseThrow().updatedAt())
        .isBetween(expected.minus(CLOCK_TOLERANCE), expected.plus(CLOCK_TOLERANCE));
  }

  @Test
  void startsWithTheLocalClockWhenTheExchangeClockIsUnavailable() {
    TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT"));
    feeder = feeder(List.of("BTCUSDT"), prices);
    rest.expect(requestTo(FakeExchangeMarketDataProtocol.SERVER_TIME_URI))
        .andRespond(withServerError());

    feeder.start();
    connections.opened().getFirst().receive("B|BTCUSDT|100.1|100.2", RECEIVED);

    assertThat(connections.opened()).hasSize(1);
    assertThat(prices.get("BTCUSDT").orElseThrow().updatedAt()).isEqualTo(RECEIVED);
  }

  @Test
  void candleUpdatesPricesAndReachesListeners() throws InterruptedException {
    TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT"));
    feeder = feeder(List.of("BTCUSDT"), prices);
    BlockingQueue<CandleUpdate> received = new LinkedBlockingQueue<>();
    feeder.addListener(received::add);
    expectServerTime(Instant.now());
    feeder.start();

    connections.opened().getFirst().receive("C|BTCUSDT|101.5|1762779600000", RECEIVED);

    CandleUpdate update = received.poll(2, TimeUnit.SECONDS);
    assertThat(update).isNotNull();
    assertThat(update.close()).isEqualByComparingTo("101.5");
    assertThat(prices.get("BTCUSDT").orElseThrow().lastPrice()).isEqualByComparingTo("101.5");
  }

  @Test
  void bidAskUpdateChangesBidAndAskOnly() {
    TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT"));
    feeder = feeder(List.of("BTCUSDT"), prices);
    List<CandleUpdate> received = new ArrayList<>();
    feeder.addListener(received::add);

    feeder.onMessage("B|BTCUSDT|100.1|100.2", RECEIVED);

    TickerQuote quote = prices.get("BTCUSDT").orElseThrow();
    assertThat(quote.bid()).isEqualByComparingTo("100.1");
    assertThat(quote.ask()).isEqualByComparingTo("100.2");
    assertThat(quote.lastPrice()).isNull();
    assertThat(quote.updatedAt()).isEqualTo(RECEIVED);
    assertThat(received).isEmpty();
  }

  @Test
  void keepsProcessingAfterUnreadableMessages() {
    TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT"));
    feeder = feeder(List.of("BTCUSDT"), prices);

    feeder.onMessage("garbage", RECEIVED);
    feeder.onMessage("ack", RECEIVED);
    assertThat(prices.get("BTCUSDT")).isEmpty();

    feeder.onMessage("B|BTCUSDT|100.1|100.2", RECEIVED);
    assertThat(prices.get("BTCUSDT")).isPresent();
  }

  @Test
  void historyPageReadsOnePageFromTheGivenStart() {
    feeder = feeder(List.of("BTCUSDT"), new TickerPriceMap(List.of("BTCUSDT")));
    rest.expect(requestTo("http://rest.test/candles?from=2")).andRespond(text("2,3"));

    CandleList page =
        feeder.historyPage(
            "BTCUSDT",
            CandleInterval.ONE_SECOND,
            Instant.ofEpochSecond(2),
            Instant.ofEpochSecond(5));

    assertThat(page.candles())
        .extracting(candle -> candle.openTime().getEpochSecond())
        .containsExactly(2L, 3L);
    assertThat(page.candles()).allMatch(CandleUpdate::closed);
    assertThat(feeder.maxCandlesPerHistoryPage())
        .isEqualTo(FakeExchangeMarketDataProtocol.MAX_CANDLES_PER_REST_HISTORY_REQUEST);
    rest.verify();
  }

  @Test
  void historyPagePassesOnAFailedRequest() {
    feeder = feeder(List.of("BTCUSDT"), new TickerPriceMap(List.of("BTCUSDT")));
    rest.expect(requestTo("http://rest.test/candles?from=0")).andRespond(withServerError());

    assertThatThrownBy(() ->
            feeder.historyPage(
                "BTCUSDT",
                CandleInterval.ONE_SECOND,
                Instant.ofEpochSecond(0),
                Instant.ofEpochSecond(5)))
        .isInstanceOf(RestClientException.class);
  }

  private CandleFeeder feeder(List<String> tickers, TickerPriceMap prices) {
    return new CandleFeeder(
        "test",
        new FakeExchangeMarketDataProtocol(),
        tickers,
        CandleInterval.ONE_MINUTE,
        prices,
        connections,
        restBuilder.build());
  }

  private static List<String> tickers(int count) {
    return IntStream.range(0, count).mapToObj(index -> "T" + index).toList();
  }

  private static List<String> streamsOf(URI connectionUri) {
    String streams =
        connectionUri
            .toString()
            .substring(FakeExchangeMarketDataProtocol.STREAM_URI_PREFIX.length());
    return List.of(streams.split(FakeExchangeMarketDataProtocol.STREAM_SEPARATOR));
  }

  private void expectServerTime(Instant serverTime) {
    rest.expect(requestTo(FakeExchangeMarketDataProtocol.SERVER_TIME_URI))
        .andRespond(text(String.valueOf(serverTime.toEpochMilli())));
  }

  private static ResponseCreator text(String body) {
    return withSuccess(body, MediaType.TEXT_PLAIN);
  }
}
