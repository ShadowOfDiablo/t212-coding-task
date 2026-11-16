package com.tihomir.t212.tradingbot.broker.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import com.tihomir.t212.tradingbot.order.balance.AccountBalanceSynchronizer;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.balance.BalanceCachingTradingGateway;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.userdata.FakeUserDataStreamConnectionFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BinanceUserDataStreamTest {

  private static final URI WEBSOCKET_API = URI.create("wss://demo-ws-api.binance.com/ws-api/v3");
  private static final Duration SYNC_INTERVAL = Duration.ofHours(1);
  private static final Duration RETRY_DELAY = Duration.ofMillis(50);
  private static final Duration SHORT_WAIT = Duration.ofSeconds(1);
  private static final String SUBSCRIBED =
      "{\"id\":\"subscribe-1\",\"status\":200,\"result\":{\"subscriptionId\":0}}";
  private static final String SUBSCRIPTION_REFUSED =
      """
      {"id":"subscribe-1","status":401,"error":{"code":-2015,"msg":"Invalid API-key"}}
      """;
  private static final String USDT_SPENT =
      """
      {"subscriptionId":0,"event":{"e":"outboundAccountPosition","E":2000,"u":2000,
       "B":[{"a":"USDT","f":"90.00000000","l":"0.00000000"}]}}
      """;
  private static final String SUBSCRIPTION_ENDED =
      "{\"subscriptionId\":0,\"event\":{\"e\":\"eventStreamTerminated\",\"E\":3000}}";

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final FakeExchangeTradingGateway exchange = new FakeExchangeTradingGateway();
  private final BalanceCachingTradingGateway gateway = new BalanceCachingTradingGateway(exchange);
  private final AccountBalanceSynchronizer synchronizer =
      new AccountBalanceSynchronizer(gateway, SYNC_INTERVAL, RETRY_DELAY);
  private final FakeUserDataStreamConnectionFactory connectionFactory =
      new FakeUserDataStreamConnectionFactory();
  private final BinanceUserDataStream stream = stream("test-api-key");

  @AfterEach
  void stop() {
    stream.close();
    synchronizer.close();
  }

  @Test
  void opensTheDemoWebSocketApiOnce() {
    stream.start();
    stream.start();

    assertThat(connectionFactory.openedUris()).containsExactly(WEBSOCKET_API);
  }

  @Test
  void staysOffWithoutAnApiKey() {
    BinanceUserDataStream streamWithoutKey = stream("");

    streamWithoutKey.start();

    assertThat(connectionFactory.openedUris()).isEmpty();
  }

  @Test
  void subscribesOnceConnected() {
    stream.start();

    stream.onConnected(connectionFactory.connection());

    assertThat(connectionFactory.connection().sentMessages())
        .singleElement()
        .asString()
        .contains(BinanceUserDataSubscription.SUBSCRIBE_METHOD);
  }

  @Test
  void successfulSubscriptionLoadsASnapshot() {
    stream.onMessage(SUBSCRIBED);

    await().atMost(SHORT_WAIT).until(() -> exchange.balanceRequests() == 1);
  }

  @Test
  void refusedSubscriptionLoadsNoSnapshot() throws InterruptedException {
    assertThatCode(() -> stream.onMessage(SUBSCRIPTION_REFUSED)).doesNotThrowAnyException();

    Thread.sleep(RETRY_DELAY.multipliedBy(2).toMillis());
    assertThat(exchange.balanceRequests()).isZero();
  }

  @Test
  void accountPositionEventUpdatesTheCachedBalances() {
    gateway.accountBalances();

    stream.onMessage(USDT_SPENT);

    assertThat(gateway.accountBalances().balances())
        .containsExactly(
            new AssetBalance("USDT", new BigDecimal("90.00000000"), new BigDecimal("0.00000000")));
    assertThat(exchange.balanceRequests()).isEqualTo(1);
  }

  @Test
  void unreadableMessagesAreLoggedAndSkipped() {
    assertThatCode(() -> stream.onMessage("not json")).doesNotThrowAnyException();
    assertThatCode(() ->
            stream.onMessage(
                "{\"subscriptionId\":0,\"event\":{\"e\":\"outboundAccountPosition\"}}"))
        .doesNotThrowAnyException();
  }

  @Test
  void endedSubscriptionIsRenewed() {
    stream.start();

    stream.onMessage(SUBSCRIPTION_ENDED);

    assertThat(connectionFactory.connection().sentMessages())
        .singleElement()
        .asString()
        .contains(BinanceUserDataSubscription.SUBSCRIBE_METHOD);
  }

  private BinanceUserDataStream stream(String apiKey) {
    BinanceTradingProperties tradingProperties =
        new BinanceTradingProperties(
            URI.create("https://demo-api.binance.com"),
            "demo-api.binance.com",
            apiKey,
            "key.pem",
            Duration.ofSeconds(5));
    BinanceUserDataSubscription subscription =
        new BinanceUserDataSubscription(
            tradingProperties,
            payload -> "signature",
            jsonMapper,
            Clock.fixed(Instant.ofEpochMilli(1), ZoneOffset.UTC));
    return new BinanceUserDataStream(
        new BinanceUserDataStreamProperties(
            WEBSOCKET_API, "demo-ws-api.binance.com", SYNC_INTERVAL, RETRY_DELAY),
        subscription,
        connectionFactory,
        jsonMapper,
        synchronizer);
  }
}
