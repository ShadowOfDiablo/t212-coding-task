package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.AbstractBroker;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnectionFactory;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Component
public class BinanceBroker extends AbstractBroker {

  private static final Logger log = LoggerFactory.getLogger(BinanceBroker.class);

  private final BinanceConnectionProperties properties;
  private final BinanceTradingProperties tradingProperties;
  private final JsonMapper jsonMapper;
  private final RestClient tradingRestClient;
  private final Optional<BinanceRequestSigner> signer;

  public BinanceBroker(
      BinanceConnectionProperties properties,
      BinanceTradingProperties tradingProperties,
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
    this.tradingProperties = tradingProperties;
    this.jsonMapper = jsonMapper;
    this.tradingRestClient = restClientBuilder.build();
    this.signer = loadSigner(properties.name(), tradingProperties);
  }

  @Override
  protected ExchangeMarketDataProtocol createMarketDataProtocol() {
    return new BinanceMarketDataProtocol(properties.streamUrl(), properties.restUrl(), jsonMapper);
  }

  @Override
  protected Optional<ExchangeTradingGateway> createTradingGateway() {
    return signer.map(this::tradingGateway);
  }

  private ExchangeTradingGateway tradingGateway(BinanceRequestSigner requestSigner) {
    return new BinanceTradingGateway(
        tradingRestClient, tradingProperties, requestSigner, jsonMapper, Clock.systemUTC());
  }

  private static Optional<BinanceRequestSigner> loadSigner(
      String brokerName, BinanceTradingProperties trading) {
    if (!trading.credentialsConfigured()) {
      log.info("{}: no demo API key configured, trading is off", brokerName);
      return Optional.empty();
    }
    log.info("{}: trading on the demo account at {}", brokerName, trading.restUrl());
    return Optional.of(BinanceRequestSigner.fromPemFile(Path.of(trading.privateKeyPath())));
  }
}
