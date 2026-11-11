package com.tihomir.t212.tradingbot.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Places real orders on the Binance demo account through the running application: buys the first
 * traded ticker, then sells it again. Runs only with {@code BINANCE_DEMO_LIVE_TESTS=true} and the
 * demo API key variables set; it is skipped in normal builds and in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "BINANCE_DEMO_LIVE_TESTS", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoAccountRoundTripLiveTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final String BROKER_PATH = "/api/brokers/binance";
  private static final String QUOTE_ASSET = "USDT";
  private static final String BUY_AMOUNT = "10";
  // Slightly less than was bought: the buy fee is taken in the coin.
  private static final String SELL_AMOUNT = "9.5";
  private static final Duration BALANCE_UPDATE_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @LocalServerPort int port;

  private RestClient api;

  @BeforeEach
  void connectToTheApplication() {
    api = RestClient.create("http://localhost:" + port + BROKER_PATH);
  }

  @Test
  @Order(1)
  void buyingTheFirstTickerOpensAPosition() {
    String ticker = firstTicker();
    String coin = baseAsset(ticker);
    BigDecimal holdingsBefore = freeBalance(coin);

    JsonNode order = placeMarketOrder(ticker, "BUY", BUY_AMOUNT);

    assertThat(order.get("exchangeStatus").asString()).isEqualTo("FILLED");
    assertThat(new BigDecimal(order.get("executedQuantity").asString())).isPositive();
    assertThat(orderHistoryIds()).contains(order.get("exchangeOrderId").asLong());
    await()
        .atMost(BALANCE_UPDATE_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .until(() -> freeBalance(coin).compareTo(holdingsBefore) > 0);
  }

  @Test
  @Order(2)
  void sellingItBringsTheHoldingsBackDown() {
    String ticker = firstTicker();
    String coin = baseAsset(ticker);
    BigDecimal holdingsBefore = freeBalance(coin);

    JsonNode order = placeMarketOrder(ticker, "SELL", SELL_AMOUNT);

    assertThat(order.get("exchangeStatus").asString()).isEqualTo("FILLED");
    assertThat(orderHistoryIds()).contains(order.get("exchangeOrderId").asLong());
    await()
        .atMost(BALANCE_UPDATE_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .until(() -> freeBalance(coin).compareTo(holdingsBefore) < 0);
  }

  private String firstTicker() {
    return get("/tickers").get("tickers").get(0).get("ticker").asString();
  }

  private static String baseAsset(String ticker) {
    assertThat(ticker).endsWith(QUOTE_ASSET);
    return ticker.substring(0, ticker.length() - QUOTE_ASSET.length());
  }

  private BigDecimal freeBalance(String asset) {
    return StreamSupport.stream(get("/balance").get("balances").spliterator(), false)
        .filter(balance -> asset.equals(balance.get("asset").asString()))
        .map(balance -> new BigDecimal(balance.get("free").asString()))
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private List<Long> orderHistoryIds() {
    return StreamSupport.stream(get("/orders/history").get("orders").spliterator(), false)
        .map(order -> order.get("exchangeOrderId").asLong())
        .toList();
  }

  private JsonNode placeMarketOrder(String ticker, String side, String quoteAmount) {
    String body =
        """
        {"ticker":"%s","side":"%s","amount":"%s","amountType":"QUOTE_AMOUNT"}
        """
            .formatted(ticker, side, quoteAmount);
    ResponseEntity<String> response =
        api.post()
            .uri("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return jsonMapper.readTree(response.getBody());
  }

  private JsonNode get(String path) {
    return jsonMapper.readTree(api.get().uri(path).retrieve().body(String.class));
  }
}
