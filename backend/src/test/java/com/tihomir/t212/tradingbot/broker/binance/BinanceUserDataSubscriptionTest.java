package com.tihomir.t212.tradingbot.broker.binance;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BinanceUserDataSubscriptionTest {

  private static final Instant NOW = Instant.ofEpochMilli(1762779782000L);
  private static final String API_KEY = "test-api-key";

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void subscribeRequestIsSignedOverTheSortedParameters() throws GeneralSecurityException {
    KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    BinanceUserDataSubscription subscription =
        new BinanceUserDataSubscription(
            new BinanceTradingProperties(
                URI.create("https://demo-api.binance.com"),
                "demo-api.binance.com",
                API_KEY,
                "key.pem",
                Duration.ofSeconds(5)),
            new BinanceRequestSigner(keyPair.getPrivate()),
            jsonMapper,
            Clock.fixed(NOW, ZoneOffset.UTC));

    JsonNode request = jsonMapper.readTree(subscription.subscribeRequest());

    assertThat(request.get("method").asString())
        .isEqualTo(BinanceUserDataSubscription.SUBSCRIBE_METHOD);
    JsonNode params = request.get("params");
    assertThat(params.get("apiKey").asString()).isEqualTo(API_KEY);
    assertThat(params.get("recvWindow").asLong()).isEqualTo(5000);
    assertThat(params.get("timestamp").asLong()).isEqualTo(NOW.toEpochMilli());
    String payload = "apiKey=" + API_KEY + "&recvWindow=5000&timestamp=" + NOW.toEpochMilli();
    assertThat(verify(keyPair, payload, params.get("signature").asString())).isTrue();
  }

  private static boolean verify(KeyPair keyPair, String payload, String base64Signature)
      throws GeneralSecurityException {
    Signature verifier = Signature.getInstance("Ed25519");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(payload.getBytes(StandardCharsets.UTF_8));
    return verifier.verify(Base64.getDecoder().decode(base64Signature));
  }
}
