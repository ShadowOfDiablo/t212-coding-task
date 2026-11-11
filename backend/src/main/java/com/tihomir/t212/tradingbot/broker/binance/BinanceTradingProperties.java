package com.tihomir.t212.tradingbot.broker.binance;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("binance.trading")
public record BinanceTradingProperties(
    URI restUrl, String demoHost, String apiKey, String privateKeyPath, Duration recvWindow) {

  private static final String NOT_SET = "";

  public BinanceTradingProperties {
    if (demoHost == null || demoHost.isBlank()) {
      throw new IllegalArgumentException("binance.trading.demo-host must be set");
    }
    if (restUrl == null || !demoHost.equals(restUrl.getHost())) {
      throw new IllegalArgumentException(
          "binance.trading.rest-url must point to the demo host " + demoHost + ", was " + restUrl);
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
