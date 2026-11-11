package com.tihomir.t212.tradingbot.broker.binance;

import org.springframework.http.HttpStatusCode;

record BinanceResponse(HttpStatusCode status, String body) {

  boolean successful() {
    return status.is2xxSuccessful();
  }
}
