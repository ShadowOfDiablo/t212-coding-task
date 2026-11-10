package com.tihomir.t212.tradingbot.marketdata.candlefeed;

import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;

@FunctionalInterface
public interface CandleUpdateListener {

  void onCandleUpdate(CandleUpdate update);
}
