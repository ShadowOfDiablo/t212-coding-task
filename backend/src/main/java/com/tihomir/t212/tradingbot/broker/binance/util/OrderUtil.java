package com.tihomir.t212.tradingbot.broker.binance.util;

import com.tihomir.t212.tradingbot.order.AccountBalanceSnapshot;
import com.tihomir.t212.tradingbot.order.AssetBalance;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderFill;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;

public final class OrderUtil {

  private static final String CLIENT_ORDER_ID_PREFIX = "tb-";
  private static final String MARKET_ORDER_TYPE = "MARKET";
  private static final String FULL_ORDER_RESPONSE = "FULL";
  private static final MathContext AVERAGE_PRICE_PRECISION = MathContext.DECIMAL64;

  private OrderUtil() {}

  public static String newClientOrderId() {
    return CLIENT_ORDER_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
  }

  public static Map<String, String> orderParameters(
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

  public static String queryString(Map<String, String> parameters) {
    return parameters.entrySet().stream()
        .map(parameter -> parameter.getKey() + "=" + encode(parameter.getValue()))
        .collect(Collectors.joining("&"));
  }

  public static ExecutedOrder executedOrder(MarketOrderRequest request, JsonNode order) {
    BigDecimal executedQuantity = decimal(order, "executedQty");
    BigDecimal quoteAmount = decimal(order, "cummulativeQuoteQty");
    List<OrderFill> fills =
        StreamSupport.stream(required(order, "fills").spliterator(), false)
            .map(OrderUtil::orderFill)
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

  public static AccountBalanceSnapshot accountBalanceSnapshot(JsonNode account) {
    List<AssetBalance> balances =
        StreamSupport.stream(required(account, "balances").spliterator(), false)
            .map(OrderUtil::assetBalance)
            .toList();
    return new AccountBalanceSnapshot(
        Instant.ofEpochMilli(required(account, "updateTime").asLong()), balances);
  }

  public static List<AssetBalance> accountPositionBalances(JsonNode accountPositionEvent) {
    return StreamSupport.stream(required(accountPositionEvent, "B").spliterator(), false)
        .map(OrderUtil::accountPositionBalance)
        .toList();
  }

  public static JsonNode required(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException("Missing field: " + field);
    }
    return value;
  }

  private static String amountParameter(OrderAmountType amountType) {
    return switch (amountType) {
      case BASE_QUANTITY -> "quantity";
      case QUOTE_AMOUNT -> "quoteOrderQty";
    };
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

  private static AssetBalance accountPositionBalance(JsonNode balance) {
    return new AssetBalance(
        required(balance, "a").asString(), decimal(balance, "f"), decimal(balance, "l"));
  }

  private static BigDecimal decimal(JsonNode node, String field) {
    return new BigDecimal(required(node, field).asString());
  }
}
