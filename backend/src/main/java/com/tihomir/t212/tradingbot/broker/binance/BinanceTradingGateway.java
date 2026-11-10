package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.order.AssetBalance;
import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.OrderStatusUnknownException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class BinanceTradingGateway implements ExchangeTradingGateway {

  static final String API_KEY_HEADER = "X-MBX-APIKEY";
  static final String ORDER_PATH = "/api/v3/order";
  static final String ACCOUNT_PATH = "/api/v3/account";
  private static final String CLIENT_ORDER_ID_PREFIX = "tb-";
  private static final String MARKET_ORDER_TYPE = "MARKET";
  private static final String FULL_ORDER_RESPONSE = "FULL";
  private static final int UNREADABLE_ERROR_CODE = 0;
  private static final MathContext AVERAGE_PRICE_PRECISION = MathContext.DECIMAL64;

  private final RestClient restClient;
  private final BinanceTradingProperties properties;
  private final BinanceRequestSigner signer;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  BinanceTradingGateway(
      RestClient restClient,
      BinanceTradingProperties properties,
      BinanceRequestSigner signer,
      JsonMapper jsonMapper,
      Clock clock) {
    this.restClient = restClient;
    this.properties = properties;
    this.signer = signer;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Override
  public ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
    String clientOrderId = newClientOrderId();
    BinanceResponse response;
    try {
      response =
          sendSignedRequest(HttpMethod.POST, ORDER_PATH, orderParameters(request, clientOrderId));
    } catch (RestClientException e) {
      throw new OrderStatusUnknownException(
          clientOrderId, "No response from Binance for the order: " + e.getMessage(), e);
    }
    if (response.status().is4xxClientError()) {
      throw rejection(response);
    }
    if (!response.status().is2xxSuccessful()) {
      throw new OrderStatusUnknownException(
          clientOrderId, "Binance answered the order with " + response.status());
    }
    try {
      return executedOrder(request, jsonMapper.readTree(response.body()));
    } catch (JacksonException | IllegalArgumentException e) {
      throw new OrderStatusUnknownException(
          clientOrderId, "Could not read Binance's answer to the order", e);
    }
  }

  @Override
  public List<AssetBalance> accountBalances() {
    BinanceResponse response;
    try {
      response =
          sendSignedRequest(HttpMethod.GET, ACCOUNT_PATH, Map.of("omitZeroBalances", "true"));
    } catch (RestClientException e) {
      throw new ExchangeUnavailableException("Could not reach Binance: " + e.getMessage(), e);
    }
    if (response.status().is4xxClientError()) {
      throw rejection(response);
    }
    if (!response.status().is2xxSuccessful()) {
      throw new ExchangeUnavailableException(
          "Binance answered the account request with " + response.status());
    }
    try {
      return StreamSupport.stream(
              required(jsonMapper.readTree(response.body()), "balances").spliterator(), false)
          .map(BinanceTradingGateway::assetBalance)
          .toList();
    } catch (JacksonException | IllegalArgumentException e) {
      throw new ExchangeUnavailableException("Could not read Binance's account response", e);
    }
  }

  private static Map<String, String> orderParameters(
      MarketOrderRequest request, String clientOrderId) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("symbol", request.ticker());
    parameters.put("side", request.side().name());
    parameters.put("type", MARKET_ORDER_TYPE);
    parameters.put(amountParameter(request.amountType()), request.amount().toPlainString());
    parameters.put("newClientOrderId", clientOrderId);
    parameters.put("newOrderRespType", FULL_ORDER_RESPONSE);
    return parameters;
  }

  private static String amountParameter(OrderAmountType amountType) {
    return switch (amountType) {
      case BASE_QUANTITY -> "quantity";
      case QUOTE_AMOUNT -> "quoteOrderQty";
    };
  }

  private BinanceResponse sendSignedRequest(
      HttpMethod method, String path, Map<String, String> parameters) {
    Map<String, String> signedParameters = new LinkedHashMap<>(parameters);
    signedParameters.put("recvWindow", String.valueOf(properties.recvWindow().toMillis()));
    signedParameters.put("timestamp", String.valueOf(clock.millis()));
    String query = queryString(signedParameters);
    String signature = URLEncoder.encode(signer.sign(query), StandardCharsets.US_ASCII);
    URI uri = URI.create(properties.restUrl() + path + "?" + query + "&signature=" + signature);
    return restClient
        .method(method)
        .uri(uri)
        .header(API_KEY_HEADER, properties.apiKey())
        .exchange(BinanceTradingGateway::readResponse);
  }

  private static BinanceResponse readResponse(
      HttpRequest request, ConvertibleClientHttpResponse response) throws IOException {
    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    return new BinanceResponse(response.getStatusCode(), body);
  }

  private static String queryString(Map<String, String> parameters) {
    return parameters.entrySet().stream()
        .map(parameter -> parameter.getKey() + "=" + encode(parameter.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private ExchangeRejectedRequestException rejection(BinanceResponse response) {
    try {
      JsonNode error = jsonMapper.readTree(response.body());
      return new ExchangeRejectedRequestException(
          required(error, "code").asInt(), required(error, "msg").asString());
    } catch (JacksonException | IllegalArgumentException e) {
      return new ExchangeRejectedRequestException(
          UNREADABLE_ERROR_CODE, "Binance answered " + response.status() + ": " + response.body());
    }
  }

  private static ExecutedOrder executedOrder(MarketOrderRequest request, JsonNode order) {
    BigDecimal executedQuantity = decimal(order, "executedQty");
    BigDecimal quoteAmount = decimal(order, "cummulativeQuoteQty");
    List<OrderFill> fills =
        StreamSupport.stream(required(order, "fills").spliterator(), false)
            .map(BinanceTradingGateway::orderFill)
            .toList();
    return new ExecutedOrder(
        required(order, "orderId").asLong(),
        required(order, "clientOrderId").asString(),
        request.ticker(),
        request.side(),
        required(order, "status").asString(),
        executedQuantity,
        quoteAmount,
        averagePrice(executedQuantity, quoteAmount),
        fills,
        Instant.ofEpochMilli(required(order, "transactTime").asLong()));
  }

  private static BigDecimal averagePrice(BigDecimal executedQuantity, BigDecimal quoteAmount) {
    if (executedQuantity.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return quoteAmount.divide(executedQuantity, AVERAGE_PRICE_PRECISION);
  }

  private static OrderFill orderFill(JsonNode fill) {
    return new OrderFill(
        required(fill, "tradeId").asLong(),
        decimal(fill, "price"),
        decimal(fill, "qty"),
        decimal(fill, "commission"),
        required(fill, "commissionAsset").asString());
  }

  private static AssetBalance assetBalance(JsonNode balance) {
    return new AssetBalance(
        required(balance, "asset").asString(),
        decimal(balance, "free"),
        decimal(balance, "locked"));
  }

  private static String newClientOrderId() {
    return CLIENT_ORDER_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
  }

  private static JsonNode required(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException("Missing field: " + field);
    }
    return value;
  }

  private static BigDecimal decimal(JsonNode node, String field) {
    return new BigDecimal(required(node, field).asString());
  }
}
