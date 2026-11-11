package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.util.List;

public record TickerPriceList(List<TickerPrice> tickers) {

  public TickerPriceList {
    tickers = List.copyOf(tickers);
  }
}
