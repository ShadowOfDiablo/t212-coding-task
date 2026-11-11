package com.tihomir.t212.tradingbot.broker.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tihomir.t212.tradingbot.broker.MissingKeyRequestSigner;
import com.tihomir.t212.tradingbot.order.AccountBalanceSnapshot;
import com.tihomir.t212.tradingbot.order.AssetBalance;
import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderSide;
import com.tihomir.t212.tradingbot.order.OrderStatusUnknownException;
import com.tihomir.t212.tradingbot.order.TradingNotConfiguredException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class BinanceTradingGatewayTest {

  private static final String API_KEY = "test-api-key";
  private static final Instant NOW = Instant.ofEpochMilli(1762779782000L);
  private static final String SIGNATURE_PARAMETER = "&signature=";
  private static final String INSUFFICIENT_BALANCE =
      "{\"code\":-2010,\"msg\":\"Account has insufficient balance for requested action.\"}";
  private static final String LOT_SIZE_FAILURE =
      "{\"code\":-1013,\"msg\":\"Filter failure: LOT_SIZE\"}";
  private static final String INVALID_API_KEY =
      "{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions for action.\"}";

  private final KeyPair keyPair = generateKeyPair();
  private final RestClient.Builder restClientBuilder = RestClient.builder();
  private final MockRestServiceServer server =
      MockRestServiceServer.bindTo(restClientBuilder).build();
  private final BinanceTradingGateway gateway =
      new BinanceTradingGateway(
          restClientBuilder,
          new BinanceTradingProperties(
              URI.create("https://demo-api.binance.com"),
              "demo-api.binance.com",
              API_KEY,
              "key.pem",
              Duration.ofSeconds(5)),
          new BinanceRequestSigner(keyPair.getPrivate()),
          JsonMapper.builder().build(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void sendsASignedMarketBuyForAQuoteAmount() throws IOException {
    server
        .expect(requestTo(Matchers.startsWith("https://demo-api.binance.com/api/v3/order?")))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(BinanceTradingGateway.API_KEY_HEADER, API_KEY))
        .andExpect(queryParam("symbol", "BTCUSDT"))
        .andExpect(queryParam("side", "BUY"))
        .andExpect(queryParam("type", "MARKET"))
        .andExpect(queryParam("quoteOrderQty", "10"))
        .andExpect(queryParam("newOrderRespType", "FULL"))
        .andExpect(queryParam("newClientOrderId", Matchers.startsWith("tb-")))
        .andExpect(queryParam("recvWindow", "5000"))
        .andExpect(queryParam("timestamp", String.valueOf(NOW.toEpochMilli())))
        .andExpect(this::signedWithTheTestKey)
        .andRespond(withSuccess(fixture("order-full-response.json"), MediaType.APPLICATION_JSON));

    ExecutedOrder order = gateway.placeMarketOrder(buyForQuoteAmount());

    assertThat(order.exchangeOrderId()).isEqualTo(28);
    assertThat(order.exchangeStatus()).isEqualTo("FILLED");
    assertThat(order.side()).isEqualTo(OrderSide.BUY);
    assertThat(order.executedQuantity()).isEqualByComparingTo("0.0002");
    assertThat(order.quoteAmount()).isEqualByComparingTo("20");
    assertThat(order.averagePrice()).isEqualByComparingTo("100000");
    assertThat(order.transactTime()).isEqualTo(Instant.ofEpochMilli(1762779782500L));
    assertThat(order.fills()).hasSize(2);
    assertThat(order.fills().getFirst().commission()).isEqualByComparingTo("0.0000001");
    assertThat(order.fills().getFirst().commissionAsset()).isEqualTo("BTC");
    server.verify();
  }

  @Test
  void sendsASellForABaseQuantity() throws IOException {
    server
        .expect(queryParam("side", "SELL"))
        .andExpect(queryParam("quantity", "0.0002"))
        .andRespond(withSuccess(fixture("order-full-response.json"), MediaType.APPLICATION_JSON));

    gateway.placeMarketOrder(
        new MarketOrderRequest(
            "BTCUSDT", OrderSide.SELL, new BigDecimal("0.0002"), OrderAmountType.BASE_QUANTITY));

    server.verify();
  }

  @Test
  void insufficientBalanceIsARejection() {
    server.expect(method(HttpMethod.POST)).andRespond(badRequest(INSUFFICIENT_BALANCE));

    assertThatThrownBy(() -> gateway.placeMarketOrder(buyForQuoteAmount()))
        .isInstanceOfSatisfying(
            ExchangeRejectedRequestException.class,
            e -> assertThat(e.exchangeErrorCode()).isEqualTo(-2010))
        .hasMessageContaining("insufficient balance");
  }

  @Test
  void filterFailureIsARejection() {
    server.expect(method(HttpMethod.POST)).andRespond(badRequest(LOT_SIZE_FAILURE));

    assertThatThrownBy(() -> gateway.placeMarketOrder(buyForQuoteAmount()))
        .isInstanceOf(ExchangeRejectedRequestException.class)
        .hasMessageContaining("LOT_SIZE");
  }

  @Test
  void unreadableRejectionKeepsTheRawAnswer() {
    server.expect(method(HttpMethod.POST)).andRespond(badRequest("<html>blocked</html>"));

    assertThatThrownBy(() -> gateway.placeMarketOrder(buyForQuoteAmount()))
        .isInstanceOf(ExchangeRejectedRequestException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  void serverErrorLeavesTheOrderStatusUnknown() {
    server.expect(method(HttpMethod.POST)).andRespond(withServerError());

    assertThatThrownBy(() -> gateway.placeMarketOrder(buyForQuoteAmount()))
        .isInstanceOfSatisfying(
            OrderStatusUnknownException.class,
            e -> assertThat(e.clientOrderId()).startsWith("tb-"));
  }

  @Test
  void connectionFailureLeavesTheOrderStatusUnknown() {
    server
        .expect(method(HttpMethod.POST))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    assertThatThrownBy(() -> gateway.placeMarketOrder(buyForQuoteAmount()))
        .isInstanceOf(OrderStatusUnknownException.class)
        .hasMessageContaining("read timed out");
  }

  @Test
  void unreadableFillLeavesTheOrderStatusUnknown() {
    server
        .expect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"orderId\":28}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> gateway.placeMarketOrder(buyForQuoteAmount()))
        .isInstanceOf(OrderStatusUnknownException.class);
  }

  @Test
  void readsAccountBalances() throws IOException {
    server
        .expect(requestTo(Matchers.startsWith("https://demo-api.binance.com/api/v3/account?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("omitZeroBalances", "true"))
        .andExpect(this::signedWithTheTestKey)
        .andRespond(withSuccess(fixture("account.json"), MediaType.APPLICATION_JSON));

    AccountBalanceSnapshot snapshot = gateway.accountBalances();

    assertThat(snapshot.updateTime()).isEqualTo(Instant.ofEpochMilli(1762779780000L));
    assertThat(snapshot.balances())
        .containsExactly(
            new AssetBalance("BTC", new BigDecimal("1.00000000"), new BigDecimal("0.00000000")),
            new AssetBalance(
                "USDT", new BigDecimal("5000.00000000"), new BigDecimal("25.50000000")));
  }

  @Test
  void invalidApiKeyIsARejectionForBalances() {
    server
        .expect(method(HttpMethod.GET))
        .andRespond(
            withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(INVALID_API_KEY));

    assertThatThrownBy(gateway::accountBalances)
        .isInstanceOf(ExchangeRejectedRequestException.class)
        .hasMessageContaining("Invalid API-key");
  }

  @Test
  void unreachableExchangeFailsBalances() {
    server
        .expect(method(HttpMethod.GET))
        .andRespond(withException(new SocketTimeoutException("connect timed out")));

    assertThatThrownBy(gateway::accountBalances).isInstanceOf(ExchangeUnavailableException.class);
  }

  @Test
  void missingKeyTurnsTradingOffWithoutSendingAnything() {
    BinanceTradingGateway gatewayWithoutKey =
        new BinanceTradingGateway(
            restClientBuilder,
            new BinanceTradingProperties(
                URI.create("https://demo-api.binance.com"),
                "demo-api.binance.com",
                "",
                "",
                Duration.ofSeconds(5)),
            new MissingKeyRequestSigner(),
            JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> gatewayWithoutKey.placeMarketOrder(buyForQuoteAmount()))
        .isInstanceOf(TradingNotConfiguredException.class);
    assertThatThrownBy(gatewayWithoutKey::accountBalances)
        .isInstanceOf(TradingNotConfiguredException.class);
    server.verify();
  }

  private void signedWithTheTestKey(ClientHttpRequest request) {
    String query = request.getURI().getRawQuery();
    int signatureStart = query.indexOf(SIGNATURE_PARAMETER);
    String payload = query.substring(0, signatureStart);
    String signature =
        URLDecoder.decode(
            query.substring(signatureStart + SIGNATURE_PARAMETER.length()),
            StandardCharsets.US_ASCII);
    assertThat(verify(payload, signature)).as("Ed25519 signature of " + payload).isTrue();
  }

  private boolean verify(String payload, String base64Signature) {
    try {
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(keyPair.getPublic());
      verifier.update(payload.getBytes(StandardCharsets.US_ASCII));
      return verifier.verify(Base64.getDecoder().decode(base64Signature));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static MarketOrderRequest buyForQuoteAmount() {
    return new MarketOrderRequest(
        "BTCUSDT", OrderSide.BUY, new BigDecimal("10"), OrderAmountType.QUOTE_AMOUNT);
  }

  private static ResponseCreator badRequest(String body) {
    return withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body);
  }

  private static KeyPair generateKeyPair() {
    try {
      return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String fixture(String name) throws IOException {
    try (InputStream input =
        BinanceTradingGatewayTest.class.getResourceAsStream("/binance/" + name)) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
