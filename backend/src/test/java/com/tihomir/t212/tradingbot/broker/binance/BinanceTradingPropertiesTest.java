package com.tihomir.t212.tradingbot.broker.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class BinanceTradingPropertiesTest {

  private static final URI DEMO = URI.create("https://demo-api.binance.com");
  private static final URI LIVE = URI.create("https://api.binance.com");
  private static final Duration RECV_WINDOW = Duration.ofSeconds(5);

  @Test
  void refusesTheLiveTradingHost() {
    assertThatThrownBy(() -> new BinanceTradingProperties(LIVE, "key", "key.pem", RECV_WINDOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("demo-api.binance.com");
  }

  @Test
  void refusesAMissingTradingHost() {
    assertThatThrownBy(() -> new BinanceTradingProperties(null, "key", "key.pem", RECV_WINDOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesANonPositiveReceiveWindow() {
    assertThatThrownBy(() -> new BinanceTradingProperties(DEMO, "key", "key.pem", Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void credentialsNeedBothTheApiKeyAndThePrivateKey() {
    assertThat(properties("key", "key.pem").credentialsConfigured()).isTrue();
    assertThat(properties(" ", "key.pem").credentialsConfigured()).isFalse();
    assertThat(properties("key", null).credentialsConfigured()).isFalse();
  }

  private static BinanceTradingProperties properties(String apiKey, String privateKeyPath) {
    return new BinanceTradingProperties(DEMO, apiKey, privateKeyPath, RECV_WINDOW);
  }
}
