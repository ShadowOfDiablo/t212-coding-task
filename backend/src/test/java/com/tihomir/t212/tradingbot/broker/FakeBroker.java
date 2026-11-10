package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.FakeExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataMessageHandler;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnection;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.web.client.RestClient;

public final class FakeBroker extends AbstractBroker {

  public static final String TRADED_TICKER = "BTCUSDT";

  private final Optional<ExchangeTradingGateway> tradingGateway;

  FakeBroker(String name) {
    this(name, Optional.empty());
  }

  public FakeBroker(String name, Optional<ExchangeTradingGateway> tradingGateway) {
    super(
        name,
        List.of(TRADED_TICKER),
        CandleInterval.ONE_MINUTE,
        FakeBroker::refuseConnection,
        RestClient.create());
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
  protected Optional<ExchangeTradingGateway> createTradingGateway() {
    return tradingGateway;
  }
}
