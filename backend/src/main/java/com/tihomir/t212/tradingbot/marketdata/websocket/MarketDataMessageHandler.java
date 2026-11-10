package com.tihomir.t212.tradingbot.marketdata.websocket;

import java.time.Instant;

@FunctionalInterface
public interface MarketDataMessageHandler {

  void onMessage(String message, Instant receivedAt);
}
