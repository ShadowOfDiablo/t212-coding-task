package com.tihomir.t212.tradingbot.marketdata.event;

public sealed interface MarketDataEvent permits CandleUpdate, BidAskUpdate {

  String ticker();
}
