package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceMap;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceReader;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnectionFactory;
import java.util.List;
import org.springframework.web.client.RestClient;

public abstract class AbstractBroker implements Broker, AutoCloseable {

  private final String name;
  private final List<String> tickers;
  private final CandleInterval interval;
  private final TickerPriceMap prices;
  private final MarketDataStreamConnectionFactory connectionFactory;
  private final RestClient restClient;
  private CandleFeeder candleFeeder;

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
  public synchronized void close() {
    if (candleFeeder != null) {
      candleFeeder.close();
    }
  }
}
