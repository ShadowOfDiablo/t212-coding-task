package com.tihomir.t212.tradingbot.broker.binance;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("binance.trading")
public record BinanceTradingProperties(
    URI restUrl, String apiKey, String privateKeyPath, Duration recvWindow) {

  static final String DEMO_TRADING_HOST = "demo-api.binance.com";
  private static final String NOT_SET = "";

  public BinanceTradingProperties {
    if (restUrl == null || !DEMO_TRADING_HOST.equals(restUrl.getHost())) {
      throw new IllegalArgumentException(
          "binance.trading.rest-url must point to the demo account at "
              + DEMO_TRADING_HOST
              + ", was "
              + restUrl);
    }
    if (recvWindow == null || recvWindow.isNegative() || recvWindow.isZero()) {
      throw new IllegalArgumentException("binance.trading.recv-window must be positive");
    }
    apiKey = valueOrNotSet(apiKey);
    privateKeyPath = valueOrNotSet(privateKeyPath);
  }

  boolean credentialsConfigured() {
    return !apiKey.isBlank() && !privateKeyPath.isBlank();
  }

  private static String valueOrNotSet(String value) {
    if (value == null) {
      return NOT_SET;
    }
    return value.strip();
  }
}
