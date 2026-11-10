package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceMap;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceReader;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnectionFactory;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import java.util.List;
import java.util.Optional;
import org.springframework.web.client.RestClient;

public abstract class AbstractBroker implements Broker, AutoCloseable {

  private final String name;
  private final List<String> tickers;
  private final CandleInterval interval;
  private final TickerPriceMap prices;
  private final MarketDataStreamConnectionFactory connectionFactory;
  private final RestClient restClient;
  private CandleFeeder candleFeeder;
  private Optional<ExchangeTradingGateway> tradingGateway = Optional.empty();
  private boolean tradingGatewayCreated;

  protected AbstractBroker(
      String name,
      List<String> tickers,
      CandleInterval interval,
      MarketDataStreamConnectionFactory connectionFactory,
      RestClient restClient) {
    this.name = name;
    this.tickers = List.copyOf(tickers);
    this.interval = interval;
    this.prices = new TickerPriceMap(this.tickers);
    this.connectionFactory = connectionFactory;
    this.restClient = restClient;
  }

  protected abstract ExchangeMarketDataProtocol createMarketDataProtocol();

  protected abstract Optional<ExchangeTradingGateway> createTradingGateway();

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final List<String> tickers() {
    return tickers;
  }

  @Override
  public final synchronized CandleFeeder candleFeeder() {
    if (candleFeeder == null) {
      candleFeeder =
          new CandleFeeder(
              name,
              createMarketDataProtocol(),
              tickers,
              interval,
              prices,
              connectionFactory,
              restClient);
    }
    return candleFeeder;
  }

  @Override
  public final TickerPriceReader tickerPrices() {
    return prices;
  }

  @Override
  public final synchronized Optional<ExchangeTradingGateway> tradingGateway() {
    if (!tradingGatewayCreated) {
      tradingGateway = createTradingGateway();
      tradingGatewayCreated = true;
    }
    return tradingGateway;
  }

  @Override
  public synchronized void close() {
    if (candleFeeder != null) {
      candleFeeder.close();
    }
  }
}
