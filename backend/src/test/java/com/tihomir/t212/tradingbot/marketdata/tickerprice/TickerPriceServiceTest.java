package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.order.FakeExchangeTradingGateway;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TickerPriceServiceTest {

  private static final String BROKER = "demo";

  private final TickerPriceService service =
      new TickerPriceService(
          new BrokerRegistryService(
              List.of(new FakeBroker(BROKER, new FakeExchangeTradingGateway()))));

  @Test
  void listsEveryTradedTickerWithoutAQuoteUntilDataArrives() {
    assertThat(service.getTickerPrices(BROKER).tickers())
        .containsExactly(new TickerPrice(FakeBroker.TRADED_TICKER, Optional.empty()));
  }

  @Test
  void refusesAnUnknownBroker() {
    assertThatThrownBy(() -> service.getTickerPrices("unknown"))
        .isInstanceOf(BrokerNotFoundException.class);
  }
}
