package com.tihomir.t212.tradingbot.marketdata.history;

public class CandleRangeTooLargeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CandleRangeTooLargeException(long requestedCandles, long maxCandles) {
    super(
        "The range holds "
            + requestedCandles
            + " candles, more than the limit of "
            + maxCandles
            + ". Choose a shorter range or a longer interval.");
  }
}
