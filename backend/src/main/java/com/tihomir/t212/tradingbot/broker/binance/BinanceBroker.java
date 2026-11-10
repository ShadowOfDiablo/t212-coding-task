package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.AbstractBroker;
import com.tihomir.t212.tradingbot.marketdata.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.stream.MarketDataStreamConnectionFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Component
public class BinanceBroker extends AbstractBroker {

  private final BinanceConnectionProperties properties;
  private final JsonMapper jsonMapper;

  public BinanceBroker(
      BinanceConnectionProperties properties,
      JsonMapper jsonMapper,
      MarketDataStreamConnectionFactory connectionFactory,
      RestClient.Builder restClientBuilder) {
    super(
        properties.name(),
        properties.tickers(),
        properties.interval(),
        connectionFactory,
        restClientBuilder.build());
    this.properties = properties;
    this.jsonMapper = jsonMapper;
  }

  @Override
  protected ExchangeMarketDataProtocol createMarketDataProtocol() {
    return new BinanceMarketDataProtocol(properties.streamUrl(), properties.restUrl(), jsonMapper);
  }
}
