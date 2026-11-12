package com.tihomir.t212.tradingbot.marketdata.candlefeed;

import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.util.List;

public record CandleList(List<CandleUpdate> candles) {

  public CandleList {
    candles = List.copyOf(candles);
  }
}
