package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.util.Iterator;
import java.util.List;

public record TickerList(List<String> tickers) implements Iterable<String> {

  public TickerList {
    tickers = List.copyOf(tickers);
  }

  public boolean contains(String ticker) {
    return tickers.contains(ticker);
  }

  public String first() {
    return tickers.getFirst();
  }

  @Override
  public Iterator<String> iterator() {
    return tickers.iterator();
  }
}
