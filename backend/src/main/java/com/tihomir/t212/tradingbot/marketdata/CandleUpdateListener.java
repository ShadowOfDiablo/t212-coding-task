package com.tihomir.t212.tradingbot.marketdata;

@FunctionalInterface
public interface CandleUpdateListener {

  void onCandleUpdate(CandleUpdate update);
}
