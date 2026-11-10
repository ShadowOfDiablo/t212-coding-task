package com.tihomir.t212.tradingbot.marketdata.price;

import java.util.Optional;
import java.util.Set;

public interface TickerPriceReader {

  Set<String> tickers();

  /** Empty until valid data for the ticker has been received. */
  Optional<TickerQuote> get(String ticker);
}
