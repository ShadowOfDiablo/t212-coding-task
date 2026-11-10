package com.tihomir.t212.tradingbot.marketdata.stream;

public interface MarketDataStreamConnection extends AutoCloseable {

  boolean isOpen();

  @Override
  void close();
}
