package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceReader;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import java.util.List;

public interface Broker {

  String name();

  List<String> tickers();

  CandleFeeder candleFeeder();

  TickerPriceReader tickerPrices();

  ExchangeTradingGateway tradingGateway();
}
