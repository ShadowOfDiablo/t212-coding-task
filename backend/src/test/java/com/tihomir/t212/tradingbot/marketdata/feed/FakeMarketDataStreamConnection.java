package com.tihomir.t212.tradingbot.marketdata.feed;

import com.tihomir.t212.tradingbot.marketdata.stream.MarketDataMessageHandler;
import com.tihomir.t212.tradingbot.marketdata.stream.MarketDataStreamConnection;
import java.time.Instant;

final class FakeMarketDataStreamConnection implements MarketDataStreamConnection {

  private final MarketDataMessageHandler handler;
  private boolean open = true;

  FakeMarketDataStreamConnection(MarketDataMessageHandler handler) {
    this.handler = handler;
  }

  void receive(String message, Instant receivedAt) {
    handler.onMessage(message, receivedAt);
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    open = false;
  }
}
