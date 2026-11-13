package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerList;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceReader;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;

public interface Broker {

  String name();

  TickerList tickers();

  CandleFeeder candleFeeder();

  TickerPriceReader tickerPrices();

  ExchangeTradingGateway tradingGateway();

  void addTicker(String ticker);

  void startAccountUpdates();
}
