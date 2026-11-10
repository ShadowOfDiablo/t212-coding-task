package com.tihomir.t212.tradingbot.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

class CandleFeederTest {

  private static final Instant RECEIVED = Instant.parse("2026-11-10T10:00:01Z");

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
    List<String> tickers = IntStream.range(0, 1300).mapToObj(i -> "T" + i).toList();
    feeder = feeder(tickers, new TickerPriceMap(tickers));

    List<List<String>> streamGroups = feeder.groupStreamsByConnection();

    assertThat(streamGroups).hasSize(3);
    assertThat(streamGroups)
        .allMatch(group -> group.size() <= FakeExchangeMarketDataProtocol.MAX_STREAMS);
    assertThat(streamGroups.get(2)).hasSize((1300 - 2 * 512) * 2);
    Set<String> streams = new HashSet<>();
    streamGroups.forEach(streams::addAll);
    assertThat(streams).hasSize(2600);
    assertThat(streamGroups.get(0))
        .contains("t511@candle", "t511@book")
        .doesNotContain("t512@candle");
  }

  @Test
  void opensOneConnectionPerStreamGroupAndClosesThemOnStop() {
    List<String> tickers = IntStream.range(0, 600).mapToObj(i -> "T" + i).toList();
    feeder = feeder(tickers, new TickerPriceMap(tickers));

    feeder.start();
    assertThat(connections.opened()).hasSize(2);
    assertThat(feeder.statistics().openConnections()).isEqualTo(2);

    feeder.stop();
    assertThat(connections.opened()).allMatch(connection -> !connection.isOpen());
  }

  @Test
  void startIsAllOrNothingWhenAConnectionFails() {
    List<String> tickers = IntStream.range(0, 1300).mapToObj(i -> "T" + i).toList();
    feeder = feeder(tickers, new TickerPriceMap(tickers));
    connections.failOnOpenNumber(3);

    assertThatThrownBy(feeder::start).isInstanceOf(IllegalStateException.class);

    assertThat(connections.opened()).hasSize(2).allMatch(connection -> !connection.isOpen());
    assertThat(feeder.statistics().connections()).isZero();
  }

  @Test
  void candleUpdatesPricesAndReachesListeners() throws InterruptedException {
    TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT"));
    feeder = feeder(List.of("BTCUSDT"), prices);
    BlockingQueue<CandleUpdate> received = new LinkedBlockingQueue<>();
    feeder.addListener(received::add);
    feeder.start();

    connections.opened().getFirst().receive("C|BTCUSDT|101.5|1762779600000", RECEIVED);

    CandleUpdate update = received.poll(2, TimeUnit.SECONDS);
    assertThat(update).isNotNull();
    assertThat(update.close()).isEqualByComparingTo("101.5");
    assertThat(prices.get("BTCUSDT").orElseThrow().lastPrice()).isEqualByComparingTo("101.5");
    assertThat(feeder.statistics().messages()).isEqualTo(1);
  }

  @Test
  void bookTickerUpdatesBidAndAskOnly() {
    TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT"));
    feeder = feeder(List.of("BTCUSDT"), prices);
    List<CandleUpdate> received = new ArrayList<>();
    feeder.addListener(received::add);

    feeder.onMessage("B|BTCUSDT|100.1|100.2", RECEIVED);

    TickerQuote quote = prices.get("BTCUSDT").orElseThrow();
    assertThat(quote.bid()).isEqualByComparingTo("100.1");
    assertThat(quote.ask()).isEqualByComparingTo("100.2");
    assertThat(quote.lastPrice()).isNull();
    assertThat(received).isEmpty();
  }

  @Test
  void countsUnreadableMessagesWithoutFailing() {
    TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT"));
    feeder = feeder(List.of("BTCUSDT"), prices);

    feeder.onMessage("garbage", RECEIVED);
    feeder.onMessage("ack", RECEIVED);

    assertThat(feeder.statistics().parseErrors()).isEqualTo(1);
    assertThat(feeder.statistics().messages()).isEqualTo(1);
    assertThat(prices.get("BTCUSDT")).isEmpty();
  }

  @Test
  void historyPagesUntilTheLastPartialPage() {
    feeder = feeder(List.of("BTCUSDT"), new TickerPriceMap(List.of("BTCUSDT")));
    rest.expect(requestTo("http://rest.test/candles?from=0")).andRespond(text("0,1"));
    rest.expect(requestTo("http://rest.test/candles?from=2")).andRespond(text("2,3"));
    rest.expect(requestTo("http://rest.test/candles?from=4")).andRespond(text("4"));

    List<CandleUpdate> candles =
        feeder.history(
            "BTCUSDT",
            CandleInterval.ONE_SECOND,
            Instant.ofEpochSecond(0),
            Instant.ofEpochSecond(5));

    assertThat(candles)
        .extracting(candle -> candle.openTime().getEpochSecond())
        .containsExactly(0L, 1L, 2L, 3L, 4L);
    rest.verify();
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

  private static ResponseCreator text(String body) {
    return withSuccess(body, MediaType.TEXT_PLAIN);
  }
}
