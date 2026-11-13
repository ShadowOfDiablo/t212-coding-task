package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TickerPriceMapTest {

  private static final Instant T1 = Instant.parse("2026-11-10T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-11-10T10:00:01Z");

  private final TickerPriceMap prices = new TickerPriceMap(List.of("BTCUSDT", "ETHUSDT"));

  @Test
  void addsAFollowedTickerOnceAtTheEndWithoutAPrice() {
    prices.addTicker("SOLUSDT");
    prices.addTicker("SOLUSDT");

    assertThat(prices.tickers()).containsExactly("BTCUSDT", "ETHUSDT", "SOLUSDT");
    assertThat(prices.get("SOLUSDT")).isEmpty();
  }

  @Test
  void startsWithoutPrices() {
    assertThat(prices.tickers()).containsExactly("BTCUSDT", "ETHUSDT");
    assertThat(prices.get("BTCUSDT")).isEmpty();
    assertThat(prices.get("ETHUSDT")).isEmpty();
  }

  @Test
  void mergesLastPriceAndBidAsk() {
    prices.updateLastPrice("BTCUSDT", new BigDecimal("100"), T1, TickerQuoteSource.WEBSOCKET);
    prices.updateBidAsk(
        "BTCUSDT",
        new BigDecimal("99.9"),
        new BigDecimal("100.1"),
        T2,
        TickerQuoteSource.WEBSOCKET);

    TickerQuote quote = prices.get("BTCUSDT").orElseThrow();
    assertThat(quote.lastPrice()).isEqualByComparingTo("100");
    assertThat(quote.bid()).isEqualByComparingTo("99.9");
    assertThat(quote.ask()).isEqualByComparingTo("100.1");
    assertThat(quote.updatedAt()).isEqualTo(T2);
    assertThat(prices.get("ETHUSDT")).isEmpty();
  }

  @Test
  void ignoresTickersThatAreNotConfigured() {
    prices.updateLastPrice("DOGEUSDT", BigDecimal.ONE, T1, TickerQuoteSource.REST);

    assertThat(prices.tickers()).doesNotContain("DOGEUSDT");
    assertThat(prices.get("DOGEUSDT")).isEmpty();
  }

  @Test
  void tickerListCannotBeModified() {
    assertThatThrownBy(() -> prices.tickers().tickers().remove("BTCUSDT"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
