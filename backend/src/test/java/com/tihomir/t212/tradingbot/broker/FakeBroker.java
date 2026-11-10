package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.FakeExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataMessageHandler;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnection;
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
