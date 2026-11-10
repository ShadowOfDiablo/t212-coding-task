package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.math.BigDecimal;
import java.time.Instant;

public interface TickerPriceWriter {

  void updateLastPrice(String ticker, BigDecimal lastPrice, Instant time, TickerQuoteSource source);

  void updateBidAsk(
      String ticker, BigDecimal bid, BigDecimal ask, Instant time, TickerQuoteSource source);
}
