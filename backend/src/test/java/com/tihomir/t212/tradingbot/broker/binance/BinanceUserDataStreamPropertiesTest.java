package com.tihomir.t212.tradingbot.broker.binance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class BinanceUserDataStreamPropertiesTest {

  private static final URI DEMO = URI.create("wss://demo-ws-api.binance.com/ws-api/v3");
  private static final URI LIVE = URI.create("wss://ws-api.binance.com:443/ws-api/v3");
  private static final Duration INTERVAL = Duration.ofMinutes(15);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

  @Test
  void refusesTheLiveWebSocketApi() {
    assertThatThrownBy(() -> new BinanceUserDataStreamProperties(LIVE, INTERVAL, RETRY_DELAY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("demo-ws-api.binance.com");
  }

  @Test
  void refusesANonPositiveSyncInterval() {
    assertThatThrownBy(() -> new BinanceUserDataStreamProperties(DEMO, Duration.ZERO, RETRY_DELAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesAMissingRetryDelay() {
    assertThatThrownBy(() -> new BinanceUserDataStreamProperties(DEMO, INTERVAL, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
