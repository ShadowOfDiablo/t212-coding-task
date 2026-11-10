package com.tihomir.t212.tradingbot.marketdata.update;

import java.time.Instant;

public sealed interface MarketDataEvent permits CandleUpdate, BidAskUpdate {

  String ticker();

  Instant eventTime();
}
