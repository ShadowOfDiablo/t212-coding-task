package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.FakeExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataMessageHandler;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnection;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.FakeExchangeTradingGateway;
import java.net.URI;
import java.util.List;
import org.springframework.web.client.RestClient;

public final class FakeBroker extends AbstractBroker {

  public static final String TRADED_TICKER = "BTCUSDT";

  private final ExchangeTradingGateway tradingGateway;

  public FakeBroker(String name, RestClient restClient) {
    this(name, new FakeExchangeTradingGateway(), restClient);
  }

  FakeBroker(String name) {
    this(name, new FakeExchangeTradingGateway());
  }

  public FakeBroker(String name, ExchangeTradingGateway tradingGateway) {
    this(name, tradingGateway, RestClient.create());
  }

  private FakeBroker(String name, ExchangeTradingGateway tradingGateway, RestClient restClient) {
    super(
        name,
        List.of(TRADED_TICKER),
        CandleInterval.ONE_MINUTE,
        FakeBroker::refuseConnection,
        restClient);
    this.tradingGateway = tradingGateway;
  }

  private static MarketDataStreamConnection refuseConnection(
      URI uri, MarketDataMessageHandler handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected ExchangeMarketDataProtocol createMarketDataProtocol() {
    return new FakeExchangeMarketDataProtocol();
  }

  @Override
  public ExchangeTradingGateway tradingGateway() {
    return tradingGateway;
  }

  @Override
  public void startAccountUpdates() {}
}
