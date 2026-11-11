package com.tihomir.t212.tradingbot.broker.binance;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("binance.user-data-stream")
public record BinanceUserDataStreamProperties(
    URI websocketApiUrl, Duration balanceSyncInterval, Duration balanceSyncRetryDelay) {

  static final String DEMO_WEBSOCKET_API_HOST = "demo-ws-api.binance.com";

  public BinanceUserDataStreamProperties {
    if (websocketApiUrl == null || !DEMO_WEBSOCKET_API_HOST.equals(websocketApiUrl.getHost())) {
      throw new IllegalArgumentException(
          "binance.user-data-stream.websocket-api-url must point to the demo account at "
              + DEMO_WEBSOCKET_API_HOST
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
