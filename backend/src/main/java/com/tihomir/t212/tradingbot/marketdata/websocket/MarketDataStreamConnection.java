package com.tihomir.t212.tradingbot.marketdata.websocket;

public interface MarketDataStreamConnection extends AutoCloseable {

  boolean isOpen();

  @Override
  void close();
}
