package com.tihomir.t212.tradingbot.broker.binance;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("binance.user-data-stream")
public record BinanceUserDataStreamProperties(
    URI websocketApiUrl,
    String demoHost,
    Duration balanceSyncInterval,
    Duration balanceSyncRetryDelay) {

  public BinanceUserDataStreamProperties {
    if (demoHost == null || demoHost.isBlank()) {
      throw new IllegalArgumentException("binance.user-data-stream.demo-host must be set");
    }
    if (websocketApiUrl == null || !demoHost.equals(websocketApiUrl.getHost())) {
      throw new IllegalArgumentException(
          "binance.user-data-stream.websocket-api-url must point to the demo host "
              + demoHost
              + ", was "
              + websocketApiUrl);
    }
    requirePositive(balanceSyncInterval, "balance-sync-interval");
    requirePositive(balanceSyncRetryDelay, "balance-sync-retry-delay");
  }

  private static void requirePositive(Duration duration, String name) {
    if (duration == null || duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException("binance.user-data-stream." + name + " must be positive");
    }
  }
}
