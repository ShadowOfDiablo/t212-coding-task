package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerList;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceMap;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceReader;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnectionFactory;
import java.util.List;
import org.springframework.web.client.RestClient;

public abstract class AbstractBroker implements Broker, AutoCloseable {

  private final String name;
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
    this.interval = interval;
    this.prices = new TickerPriceMap(tickers);
    this.connectionFactory = connectionFactory;
    this.restClient = restClient;
  }

  protected abstract ExchangeMarketDataProtocol createMarketDataProtocol();

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final TickerList tickers() {
    return prices.tickers();
  }

  /**
   * Starts following a ticker: its prices are kept and its streams are opened. Following a ticker
   * that is already followed changes nothing.
   */
  @Override
  public final synchronized void addTicker(String ticker) {
    if (prices.tickers().contains(ticker)) {
      return;
    }
    prices.addTicker(ticker);
    candleFeeder().addTicker(ticker);
  }

  @Override
  public final synchronized CandleFeeder candleFeeder() {
    if (candleFeeder == null) {
      candleFeeder =
          new CandleFeeder(
              name,
              createMarketDataProtocol(),
              prices.tickers().tickers(),
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
