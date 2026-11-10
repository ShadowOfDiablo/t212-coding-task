package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.FakeExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.stream.MarketDataMessageHandler;
import com.tihomir.t212.tradingbot.marketdata.stream.MarketDataStreamConnection;
import java.net.URI;
import java.util.List;
import org.springframework.web.client.RestClient;

final class FakeBroker extends AbstractBroker {

  FakeBroker(String name) {
    super(
        name,
        List.of("BTCUSDT"),
        CandleInterval.ONE_MINUTE,
        FakeBroker::refuseConnection,
        RestClient.create());
  }

  private static MarketDataStreamConnection refuseConnection(
      URI uri, MarketDataMessageHandler handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected ExchangeMarketDataProtocol createMarketDataProtocol() {
    return new FakeExchangeMarketDataProtocol();
  }
}
