package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.util.Optional;

public interface TickerPriceReader {

  TickerList tickers();

  /** Empty until valid data for the ticker has been received. */
  Optional<TickerQuote> get(String ticker);
}
