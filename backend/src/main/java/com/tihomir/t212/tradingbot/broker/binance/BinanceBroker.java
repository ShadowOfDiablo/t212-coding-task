package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.AbstractBroker;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnectionFactory;
import com.tihomir.t212.tradingbot.order.BalanceCachingTradingGateway;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Component
public class BinanceBroker extends AbstractBroker {

  private final BinanceConnectionProperties properties;
  private final JsonMapper jsonMapper;
  private final BalanceCachingTradingGateway tradingGateway;

  public BinanceBroker(
      BinanceConnectionProperties properties,
      JsonMapper jsonMapper,
      MarketDataStreamConnectionFactory connectionFactory,
      RestClient.Builder restClientBuilder,
      BinanceTradingGateway tradingGateway) {
    super(
        properties.name(),
        properties.tickers(),
        properties.interval(),
        connectionFactory,
        restClientBuilder.build());
    this.properties = properties;
    this.jsonMapper = jsonMapper;
    this.tradingGateway = new BalanceCachingTradingGateway(tradingGateway);
  }

  @Override
  protected ExchangeMarketDataProtocol createMarketDataProtocol() {
    return new BinanceMarketDataProtocol(properties.streamUrl(), properties.restUrl(), jsonMapper);
  }

  @Override
  public ExchangeTradingGateway tradingGateway() {
    return tradingGateway;
  }
}
