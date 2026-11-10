package com.tihomir.t212.tradingbot.broker.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.marketdata.BestBidAskUpdate;
import com.tihomir.t212.tradingbot.marketdata.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.CandleUpdate;
import com.tihomir.t212.tradingbot.marketdata.MarketDataEvent;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BinanceMarketDataProtocolTest {

  private final BinanceMarketDataProtocol protocol =
      new BinanceMarketDataProtocol(
          URI.create("wss://stream.test"),
          URI.create("https://rest.test"),
          JsonMapper.builder().build());

  @Test
  void buildsCandleAndBookTickerStreamNames() {
    assertThat(protocol.streamNames("BTCUSDT", CandleInterval.ONE_MINUTE))
        .containsExactly("btcusdt@kline_1m", "btcusdt@bookTicker");
  }

  @Test
  void mapsEveryIntervalToABinanceCodeAndBack() {
    assertThat(BinanceMarketDataProtocol.intervalCode(CandleInterval.ONE_SECOND)).isEqualTo("1s");
    assertThat(BinanceMarketDataProtocol.intervalCode(CandleInterval.FOUR_HOURS)).isEqualTo("4h");
    for (CandleInterval interval : CandleInterval.values()) {
      String code = BinanceMarketDataProtocol.intervalCode(interval);
      assertThat(BinanceMarketDataProtocol.intervalFromCode(code)).isEqualTo(interval);
    }
    assertThatThrownBy(() -> BinanceMarketDataProtocol.intervalFromCode("7m"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void combinesStreamsIntoOneConnectionUri() {
    URI uri = protocol.connectionUri(List.of("btcusdt@kline_1m", "btcusdt@bookTicker"));

    assertThat(uri)
        .hasToString("wss://stream.test/stream?streams=btcusdt@kline_1m/btcusdt@bookTicker");
  }

  @Test
  void parsesCandleMessage() throws IOException {
    Optional<MarketDataEvent> event = protocol.parse(fixture("candle-stream.json"));

    assertThat(event).containsInstanceOf(CandleUpdate.class);
    CandleUpdate candle = (CandleUpdate) event.orElseThrow();
    assertThat(candle.ticker()).isEqualTo("BTCUSDT");
    assertThat(candle.interval()).isEqualTo(CandleInterval.ONE_MINUTE);
    assertThat(candle.openTime()).isEqualTo(Instant.ofEpochMilli(1762779780000L));
    assertThat(candle.close()).isEqualByComparingTo("101520.55");
    assertThat(candle.volume()).isEqualByComparingTo("12.345");
    assertThat(candle.closed()).isFalse();
    assertThat(candle.eventTime()).isEqualTo(Instant.ofEpochMilli(1762779782136L));
  }

  @Test
  void parsesBookTickerMessage() throws IOException {
    Optional<MarketDataEvent> event = protocol.parse(fixture("book-ticker-stream.json"));

    assertThat(event)
        .contains(
            new BestBidAskUpdate(
                "BTCUSDT", new BigDecimal("101520.50000000"), new BigDecimal("101520.60000000")));
  }

  @Test
  void ignoresSubscriptionAcknowledgements() {
    assertThat(protocol.parse("{\"result\":null,\"id\":1}")).isEmpty();
  }

  @Test
  void rejectsMalformedMessages() {
    assertThatThrownBy(() -> protocol.parse("not json")).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> protocol.parse("{\"stream\":\"btcusdt@bookTicker\",\"data\":{}}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesHistoricalCandlesAsClosed() throws IOException {
    List<CandleUpdate> candles =
        protocol.parseCandles(fixture("candles.json"), "BTCUSDT", CandleInterval.ONE_MINUTE);

    assertThat(candles).hasSize(2);
    assertThat(candles).allMatch(CandleUpdate::closed);
    assertThat(candles.get(1).close()).isEqualByComparingTo("101520.55");
    assertThat(candles.get(1).eventTime()).isEqualTo(candles.get(1).closeTime());
  }

  @Test
  void buildsCandlesUriWithExclusiveEnd() {
    URI uri =
        protocol.candlesUri(
            "BTCUSDT",
            CandleInterval.ONE_SECOND,
            Instant.ofEpochMilli(1000),
            Instant.ofEpochMilli(5000),
            1000);

    assertThat(uri)
        .hasToString(
            "https://rest.test/api/v3/klines?symbol=BTCUSDT&interval=1s"
                + "&startTime=1000&endTime=4999&limit=1000");
  }

  static String fixture(String name) throws IOException {
    try (InputStream in =
        BinanceMarketDataProtocolTest.class.getResourceAsStream("/binance/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
