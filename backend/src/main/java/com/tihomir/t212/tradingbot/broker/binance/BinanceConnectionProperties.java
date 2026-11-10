package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("binance")
public record BinanceConnectionProperties(
    String name, URI streamUrl, URI restUrl, CandleInterval interval, List<String> tickers) {

  public BinanceConnectionProperties {
    if (tickers == null || tickers.isEmpty()) {
      throw new IllegalArgumentException("binance.tickers must contain at least one ticker");
    }
    tickers = List.copyOf(tickers);
  }
}
