package com.tihomir.t212.tradingbot.marketdata.feed;

import com.tihomir.t212.tradingbot.marketdata.event.CandleUpdate;

@FunctionalInterface
public interface CandleUpdateListener {

  void onCandleUpdate(CandleUpdate update);
}
