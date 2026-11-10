package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.math.BigDecimal;
import java.time.Instant;

public record TickerQuote(
    BigDecimal bid,
    BigDecimal ask,
    BigDecimal lastPrice,
    Instant updatedAt,
    TickerQuoteSource source) {

  TickerQuote withLastPrice(BigDecimal newLastPrice, Instant time, TickerQuoteSource newSource) {
    return new TickerQuote(bid, ask, newLastPrice, time, newSource);
  }

  TickerQuote withBidAsk(
      BigDecimal newBid, BigDecimal newAsk, Instant time, TickerQuoteSource newSource) {
    return new TickerQuote(newBid, newAsk, lastPrice, time, newSource);
  }
}
