package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.marketdata.feed.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.price.TickerPriceReader;
import java.util.List;

public interface Broker {

  String name();

  List<String> tickers();

  CandleFeeder candleFeeder();

  TickerPriceReader tickerPrices();
}
