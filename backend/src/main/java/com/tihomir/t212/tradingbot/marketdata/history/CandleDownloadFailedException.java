package com.tihomir.t212.tradingbot.marketdata.history;

import java.time.Instant;

public class CandleDownloadFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CandleDownloadFailedException(
      CandleHistoryRequest request, Instant pageStart, Throwable cause) {
    super(
        "Downloading "
            + request.ticker()
            + " "
            + request.interval()
            + " candles from "
            + pageStart
            + " failed",
        cause);
  }
}
