package com.tihomir.t212.tradingbot.broker.binance.util;

import com.tihomir.t212.tradingbot.order.AccountBalanceList;
import com.tihomir.t212.tradingbot.order.AccountBalanceSnapshot;
import com.tihomir.t212.tradingbot.order.AssetBalance;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderFill;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;

public final class OrderUtil {

  private static final String CLIENT_ORDER_ID_PREFIX = "tb-";
  private static final String FULL_ORDER_RESPONSE = "FULL";
  private static final MathContext AVERAGE_PRICE_PRECISION = MathContext.DECIMAL64;
  private static final String LOT_SIZE_FILTER = "LOT_SIZE";
  private static final int FIRST_SYMBOL = 0;

  private OrderUtil() {}

  /** Marks the order as the bot's, so orders placed by hand can be told apart. */
  public static String newClientOrderId() {
    return CLIENT_ORDER_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
  }

  public static QueryParameters orderParameters(MarketOrderRequest request, String clientOrderId) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("symbol", request.ticker());
    parameters.put("side", request.side().name());
    parameters.put("type", MarketOrderRequest.ORDER_TYPE);
    parameters.put(amountParameter(request.amountType()), request.amount().toPlainString());
    parameters.put("newClientOrderId", clientOrderId);
    parameters.put("newOrderRespType", FULL_ORDER_RESPONSE);
    return new QueryParameters(parameters);
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

  public static AccountBalanceList accountPositionBalances(JsonNode accountPositionEvent) {
    return new AccountBalanceList(
        StreamSupport.stream(required(accountPositionEvent, "B").spliterator(), false)
            .map(OrderUtil::accountPositionBalance)
            .toList());
  }

  /**
   * The step every base quantity of the symbol must be a multiple of, from its LOT_SIZE filter.
   *
   * @throws IllegalArgumentException if the answer has no usable LOT_SIZE filter
   */
  public static BigDecimal lotStepSize(JsonNode exchangeInfo) {
    JsonNode symbols = required(exchangeInfo, "symbols");
    if (symbols.isEmpty()) {
      throw new IllegalArgumentException("Binance listed no symbol");
    }
    BigDecimal lotStepSize =
        StreamSupport.stream(required(symbols.get(FIRST_SYMBOL), "filters").spliterator(), false)
            .filter(filter -> LOT_SIZE_FILTER.equals(required(filter, "filterType").asString()))
            .map(filter -> decimal(filter, "stepSize"))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Missing LOT_SIZE filter"));
    if (lotStepSize.signum() <= 0) {
      throw new IllegalArgumentException("LOT_SIZE step must be greater than zero");
    }
    return lotStepSize;
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
