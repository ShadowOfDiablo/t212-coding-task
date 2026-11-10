package com.tihomir.t212.tradingbot.marketdata.stream;

import java.net.URI;

public interface MarketDataStreamConnectionFactory {

  MarketDataStreamConnection open(URI uri, MarketDataMessageHandler handler);
}
