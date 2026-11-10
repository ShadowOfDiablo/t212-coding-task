package com.tihomir.t212.tradingbot.marketdata.candlefeed;

import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataMessageHandler;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnection;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnectionFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

final class FakeMarketDataStreamConnectionFactory implements MarketDataStreamConnectionFactory {

  private final List<FakeMarketDataStreamConnection> opened = new ArrayList<>();
  private int failingOpenNumber;

  @Override
  public MarketDataStreamConnection open(URI uri, MarketDataMessageHandler handler) {
    if (opened.size() + 1 == failingOpenNumber) {
      throw new IllegalStateException("cannot connect to " + uri);
    }
    FakeMarketDataStreamConnection connection = new FakeMarketDataStreamConnection(handler);
    opened.add(connection);
    return connection;
  }

  void failOnOpenNumber(int openNumber) {
    failingOpenNumber = openNumber;
  }

  List<FakeMarketDataStreamConnection> opened() {
    return opened;
  }
}
