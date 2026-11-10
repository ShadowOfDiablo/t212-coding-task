package com.tihomir.t212.tradingbot.marketdata;

public sealed interface MarketDataEvent permits CandleUpdate, BestBidAskUpdate {

  String ticker();
}
