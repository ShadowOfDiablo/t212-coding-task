package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.RequestSigner;
import com.tihomir.t212.tradingbot.broker.binance.util.OrderUtil;
import com.tihomir.t212.tradingbot.broker.binance.util.QueryParameters;
import com.tihomir.t212.tradingbot.order.AccountBalanceSnapshot;
import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderStatusUnknownException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
final class BinanceTradingGateway implements ExchangeTradingGateway {

  static final String API_KEY_HEADER = "X-MBX-APIKEY";
  static final String ORDER_PATH = "/api/v3/order";
  static final String ACCOUNT_PATH = "/api/v3/account";
  static final String EXCHANGE_INFO_PATH = "/api/v3/exchangeInfo";
  private static final int UNREADABLE_ERROR_CODE = 0;
  private static final int LOT_SIZE_ERROR_CODE = -1013;

  private final Map<String, BigDecimal> lotStepSizeByTicker = new ConcurrentHashMap<>();

  private final RestClient restClient;
  private final BinanceTradingProperties tradingProperties;
  private final RequestSigner signer;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  BinanceTradingGateway(
      RestClient.Builder restClientBuilder,
      BinanceTradingProperties tradingProperties,
      RequestSigner signer,
      JsonMapper jsonMapper,
      Clock clock) {
    this.restClient = restClientBuilder.build();
    this.tradingProperties = tradingProperties;
    this.signer = signer;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  /**
   * A base quantity is first rounded down to the ticker's lot step, which Binance requires. The
   * step is read once per ticker; if it can't be read, nothing is sent.
   */
  @Override
  public ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
    MarketOrderRequest lotSizedRequest = roundedToLotStep(request);
    String clientOrderId = OrderUtil.newClientOrderId();
    try {
      BinanceResponse response =
          sendSignedRequest(
              HttpMethod.POST,
              ORDER_PATH,
              OrderUtil.orderParameters(lotSizedRequest, clientOrderId));
      if (!response.successful()) {
        throw orderFailure(response, clientOrderId);
      }
      return OrderUtil.executedOrder(lotSizedRequest, jsonMapper.readTree(response.body()));
    } catch (RestClientException e) {
      throw new OrderStatusUnknownException(
          clientOrderId, "No response from Binance for the order: " + e.getMessage(), e);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new OrderStatusUnknownException(
          clientOrderId, "Could not read Binance's answer to the order", e);
    }
  }

  @Override
  public AccountBalanceSnapshot accountBalances() {
    try {
      BinanceResponse response =
          sendSignedRequest(
              HttpMethod.GET,
              ACCOUNT_PATH,
              new QueryParameters(Map.of("omitZeroBalances", "true")));
      if (!response.successful()) {
        throw accountFailure(response);
      }
      return OrderUtil.accountBalanceSnapshot(jsonMapper.readTree(response.body()));
    } catch (RestClientException e) {
      throw new ExchangeUnavailableException("Could not reach Binance: " + e.getMessage(), e);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new ExchangeUnavailableException("Could not read Binance's account response", e);
    }
  }

  private MarketOrderRequest roundedToLotStep(MarketOrderRequest request) {
    if (request.amountType() != OrderAmountType.BASE_QUANTITY) {
      return request;
    }
    BigDecimal lotStepSize =
        lotStepSizeByTicker.computeIfAbsent(request.ticker(), this::readLotStepSize);
    BigDecimal quantity = OrderUtil.roundDownToLotStep(request.amount(), lotStepSize);
    if (quantity.signum() <= 0) {
      throw new ExchangeRejectedRequestException(
          LOT_SIZE_ERROR_CODE,
          request.amount().toPlainString()
              + " "
              + request.ticker()
              + " is less than one lot step of "
              + lotStepSize.toPlainString());
    }
    return new MarketOrderRequest(
        request.ticker(), request.side(), quantity, OrderAmountType.BASE_QUANTITY);
  }

  private BigDecimal readLotStepSize(String ticker) {
    try {
      String body =
          restClient
              .get()
              .uri(
                  URI.create(
                      tradingProperties.restUrl() + EXCHANGE_INFO_PATH + "?symbol=" + ticker))
              .retrieve()
              .body(String.class);
      return OrderUtil.lotStepSize(jsonMapper.readTree(body));
    } catch (RestClientException e) {
      throw new ExchangeUnavailableException(
          "Could not read the lot size of " + ticker + " from Binance: " + e.getMessage(), e);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new ExchangeUnavailableException("Could not read Binance's lot size of " + ticker, e);
    }
  }

  private BinanceResponse sendSignedRequest(
      HttpMethod method, String path, QueryParameters parameters) {
    QueryParameters signedParameters =
        parameters
            .withParameter("recvWindow", String.valueOf(tradingProperties.recvWindow().toMillis()))
            .withParameter("timestamp", String.valueOf(clock.millis()));
    return restClient
        .method(method)
        .uri(signedUri(path, signedParameters.queryString()))
        .header(API_KEY_HEADER, tradingProperties.apiKey())
        .exchange(BinanceTradingGateway::readResponse);
  }

  private URI signedUri(String path, String query) {
    String signature = URLEncoder.encode(signer.sign(query), StandardCharsets.US_ASCII);
    return URI.create(tradingProperties.restUrl() + path + "?" + query + "&signature=" + signature);
  }

  private static BinanceResponse readResponse(
      HttpRequest request, ConvertibleClientHttpResponse response) throws IOException {
    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    return new BinanceResponse(response.getStatusCode(), body);
  }

  private RuntimeException orderFailure(BinanceResponse response, String clientOrderId) {
    if (response.status().is4xxClientError()) {
      return rejection(response);
    }
    return new OrderStatusUnknownException(
        clientOrderId, "Binance answered the order with " + response.status());
  }

  private RuntimeException accountFailure(BinanceResponse response) {
    if (response.status().is4xxClientError()) {
      return rejection(response);
    }
    return new ExchangeUnavailableException(
        "Binance answered the account request with " + response.status());
  }

  private ExchangeRejectedRequestException rejection(BinanceResponse response) {
    try {
      JsonNode error = jsonMapper.readTree(response.body());
      return new ExchangeRejectedRequestException(
          OrderUtil.required(error, "code").asInt(), OrderUtil.required(error, "msg").asString());
    } catch (JacksonException | IllegalArgumentException e) {
      return new ExchangeRejectedRequestException(
          UNREADABLE_ERROR_CODE, "Binance answered " + response.status() + ": " + response.body());
    }
  }
}
