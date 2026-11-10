package com.tihomir.t212.tradingbot.marketdata.update;

public sealed interface MarketDataEvent permits CandleUpdate, BidAskUpdate {

  String ticker();
}
