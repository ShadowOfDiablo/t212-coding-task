package com.tihomir.t212.tradingbot.marketdata.stream;

import java.time.Instant;

@FunctionalInterface
public interface MarketDataMessageHandler {

  void onMessage(String message, Instant receivedAt);
}
