package com.tihomir.t212.tradingbot.broker.binance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class BinanceUserDataStreamPropertiesTest {

  private static final URI DEMO = URI.create("wss://demo-ws-api.binance.com/ws-api/v3");
  private static final String DEMO_HOST = "demo-ws-api.binance.com";
  private static final URI LIVE = URI.create("wss://ws-api.binance.com:443/ws-api/v3");
  private static final Duration INTERVAL = Duration.ofMinutes(15);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

  @Test
  void refusesTheLiveWebSocketApi() {
    assertThatThrownBy(
            () -> new BinanceUserDataStreamProperties(LIVE, DEMO_HOST, INTERVAL, RETRY_DELAY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("demo-ws-api.binance.com");
  }

  @Test
  void refusesAMissingDemoHost() {
    assertThatThrownBy(() -> new BinanceUserDataStreamProperties(DEMO, null, INTERVAL, RETRY_DELAY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("demo-host");
  }

  @Test
  void refusesANonPositiveSyncInterval() {
    assertThatThrownBy(
            () -> new BinanceUserDataStreamProperties(DEMO, DEMO_HOST, Duration.ZERO, RETRY_DELAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesAMissingRetryDelay() {
    assertThatThrownBy(() -> new BinanceUserDataStreamProperties(DEMO, DEMO_HOST, INTERVAL, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
