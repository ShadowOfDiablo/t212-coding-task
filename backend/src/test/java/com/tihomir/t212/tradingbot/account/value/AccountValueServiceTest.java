package com.tihomir.t212.tradingbot.account.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceWriter;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuoteSource;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.exception.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountValueServiceTest {

  private static final String BROKER = "binance";
  private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final FakeBroker broker = new FakeBroker(BROKER, gateway);
  private final AccountValueService service =
      new AccountValueService(
          new BrokerRegistryService(List.of(broker)), Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void holdCashACoinWithAPriceAndOneWithout() {
    gateway.changeBalances(
        NOW,
        List.of(
            new AssetBalance("USDT", new BigDecimal("100"), BigDecimal.TEN),
            new AssetBalance("BTC", new BigDecimal("0.5"), BigDecimal.ZERO),
            new AssetBalance("XYZ", new BigDecimal("3"), BigDecimal.ZERO)));
    ((TickerPriceWriter) broker.tickerPrices())
        .updateLastPrice(
            FakeBroker.TRADED_TICKER, new BigDecimal("200"), NOW, TickerQuoteSource.WEBSOCKET);
  }

  @Test
  void valuesCashAndPricedCoinsAndLeavesOutACoinWithoutAPrice() {
    service.recordAccountValues();

    assertThat(service.getAccountValuePoints(BROKER).points())
        .singleElement()
        .satisfies(point -> {
          assertThat(point.time()).isEqualTo(NOW);
          assertThat(point.value()).isEqualByComparingTo("210");
        });
  }

  @Test
  void skipsASampleWhoseBalancesCannotBeReadAndKeepsSampling() {
    gateway.failBalanceRequests(new ExchangeUnavailableException("timed out"), 1);

    service.recordAccountValues();
    service.recordAccountValues();

    assertThat(service.getAccountValuePoints(BROKER).points()).hasSize(1);
  }

  @Test
  void refusesAnUnknownBroker() {
    assertThatThrownBy(() -> service.getAccountValuePoints("kraken"))
        .isInstanceOf(BrokerNotFoundException.class);
  }
}
