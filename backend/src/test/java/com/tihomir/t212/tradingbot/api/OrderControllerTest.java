package com.tihomir.t212.tradingbot.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.order.OrderService;
import com.tihomir.t212.tradingbot.order.balance.AccountBalanceList;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.exception.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.exception.OrderStatusUnknownException;
import com.tihomir.t212.tradingbot.order.exception.TickerNotTradedException;
import com.tihomir.t212.tradingbot.order.exception.TradingNotConfiguredException;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.OpenOrder;
import com.tihomir.t212.tradingbot.order.model.OpenOrderList;
import com.tihomir.t212.tradingbot.order.model.OpenOrderStatus;
import com.tihomir.t212.tradingbot.order.model.OrderAmountType;
import com.tihomir.t212.tradingbot.order.model.OrderFee;
import com.tihomir.t212.tradingbot.order.model.OrderHistory;
import com.tihomir.t212.tradingbot.order.model.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import com.tihomir.t212.tradingbot.order.model.RejectedOrder;
import com.tihomir.t212.tradingbot.order.model.RejectedOrderList;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

  private static final String ORDERS = "/api/brokers/binance/orders";
  private static final String BUY_FOR_TEN_USDT =
      """
      {"ticker":"BTCUSDT","side":"BUY","amount":"10","amountType":"QUOTE_AMOUNT"}
      """;
  private static final MarketOrderRequest PARSED_BUY =
      new MarketOrderRequest(
          "BTCUSDT", OrderSide.BUY, new BigDecimal("10"), OrderAmountType.QUOTE_AMOUNT);

  @Autowired MockMvc mockMvc;

  @MockitoBean OrderService orderService;

  @Test
  void placedOrderIsCreated() throws Exception {
    when(orderService.placeMarketOrder("binance", PARSED_BUY)).thenReturn(executedBuy());

    postBuy()
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.clientOrderId").value("tb-1"))
        .andExpect(jsonPath("$.exchangeStatus").value("FILLED"))
        .andExpect(jsonPath("$.transactTime").value("2025-11-10T13:03:02.500Z"));
  }

  @Test
  void balancesAreListed() throws Exception {
    when(orderService.getAccountBalances("binance"))
        .thenReturn(
            new AccountBalanceList(
                List.of(new AssetBalance("USDT", new BigDecimal("5000"), BigDecimal.ZERO))));

    mockMvc
        .perform(get("/api/brokers/binance/balance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balances[0].asset").value("USDT"));
  }

  @Test
  void amountsArePlainDecimals() throws Exception {
    when(orderService.getAccountBalances("binance"))
        .thenReturn(
            new AccountBalanceList(
                List.of(
                    new AssetBalance("BTC", new BigDecimal("1.3E-7"), new BigDecimal("0E-8")))));

    mockMvc
        .perform(get("/api/brokers/binance/balance"))
        .andExpect(
            content()
                .string(
                    "{\"balances\":[{\"asset\":\"BTC\",\"free\":0.00000013,\"locked\":0.00000000}]}"));
  }

  @Test
  void invalidAmountIsABadRequest() throws Exception {
    mockMvc
        .perform(
            post(ORDERS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ticker":"BTCUSDT","side":"BUY","amount":"-1","amountType":"QUOTE_AMOUNT"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tickerNotTradedIsABadRequest() throws Exception {
    whenBuying().thenThrow(new TickerNotTradedException("binance", "BTCUSDT"));

    postBuy().andExpect(status().isBadRequest());
  }

  @Test
  void unknownBrokerIsNotFound() throws Exception {
    whenBuying().thenThrow(new BrokerNotFoundException("binance"));

    postBuy().andExpect(status().isNotFound());
  }

  @Test
  void missingKeysIsAConflict() throws Exception {
    whenBuying().thenThrow(new TradingNotConfiguredException());

    postBuy().andExpect(status().isConflict());
  }

  @Test
  void exchangeRejectionIsUnprocessableWithTheExchangeCode() throws Exception {
    whenBuying().thenThrow(new ExchangeRejectedRequestException(-2010, "insufficient balance"));

    postBuy()
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.exchangeErrorCode").value(-2010))
        .andExpect(jsonPath("$.detail").value("insufficient balance"));
  }

  @Test
  void unknownOrderStatusIsABadGatewayWithTheClientOrderId() throws Exception {
    whenBuying().thenThrow(new OrderStatusUnknownException("tb-1", "read timed out"));

    postBuy()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.clientOrderId").value("tb-1"));
  }

  @Test
  void unreachableExchangeIsABadGateway() throws Exception {
    when(orderService.getAccountBalances("binance"))
        .thenThrow(new ExchangeUnavailableException("connect timed out"));

    mockMvc.perform(get("/api/brokers/binance/balance")).andExpect(status().isBadGateway());
  }

  @Test
  void databaseDownIsServiceUnavailable() throws Exception {
    when(orderService.getOrderHistory("binance"))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));

    mockMvc.perform(get(ORDERS + "/history")).andExpect(status().isServiceUnavailable());
  }

  @Test
  void openOrdersAreListedByExchangeOrderId() throws Exception {
    when(orderService.getOpenOrders("binance"))
        .thenReturn(
            new OpenOrderList(
                List.of(
                    new OpenOrder(
                        30,
                        "binance",
                        "BTCUSDT",
                        OrderSide.SELL,
                        "TAKE_PROFIT_LIMIT",
                        new BigDecimal("0.0001"),
                        OrderAmountType.BASE_QUANTITY,
                        OpenOrderStatus.NEW,
                        Instant.EPOCH,
                        Instant.EPOCH))));

    mockMvc
        .perform(get(ORDERS + "/open"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openOrders[0].exchangeOrderId").value(30))
        .andExpect(jsonPath("$.openOrders[0].status").value("NEW"));
  }

  @Test
  void orderHistoryIsListedWithTheFee() throws Exception {
    when(orderService.getOrderHistory("binance"))
        .thenReturn(
            new OrderHistory(
                List.of(
                    new OrderHistoryEntry(
                        28L,
                        "binance",
                        "BTCUSDT",
                        OrderSide.BUY,
                        MarketOrderRequest.ORDER_TYPE,
                        BigDecimal.TEN,
                        OrderAmountType.QUOTE_AMOUNT,
                        "FILLED",
                        new BigDecimal("0.0001"),
                        BigDecimal.TEN,
                        new BigDecimal("100000"),
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Optional.of(new OrderFee(new BigDecimal("0.0000001"), "BTC"))))));

    mockMvc
        .perform(get(ORDERS + "/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders[0].exchangeOrderId").value(28))
        .andExpect(jsonPath("$.orders[0].finalStatus").value("FILLED"))
        .andExpect(jsonPath("$.orders[0].fee.currency").value("BTC"));
  }

  @Test
  void rejectedOrdersAreListedWithTheExchangeError() throws Exception {
    when(orderService.getRejectedOrders("binance"))
        .thenReturn(
            new RejectedOrderList(
                List.of(
                    new RejectedOrder(
                        "binance",
                        "BTCUSDT",
                        OrderSide.SELL,
                        MarketOrderRequest.ORDER_TYPE,
                        new BigDecimal("0.00013"),
                        OrderAmountType.BASE_QUANTITY,
                        -2010,
                        "insufficient balance",
                        Instant.EPOCH))));

    mockMvc
        .perform(get(ORDERS + "/rejected"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rejectedOrders[0].ticker").value("BTCUSDT"))
        .andExpect(jsonPath("$.rejectedOrders[0].errorCode").value(-2010))
        .andExpect(jsonPath("$.rejectedOrders[0].errorMessage").value("insufficient balance"));
  }

  private OngoingStubbing<ExecutedOrder> whenBuying() {
    return when(orderService.placeMarketOrder(eq("binance"), any(MarketOrderRequest.class)));
  }

  private ResultActions postBuy() throws Exception {
    return mockMvc.perform(
        post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(BUY_FOR_TEN_USDT));
  }

  private static ExecutedOrder executedBuy() {
    return new ExecutedOrder(
        28,
        "tb-1",
        "BTCUSDT",
        OrderSide.BUY,
        "FILLED",
        new BigDecimal("0.0001"),
        new BigDecimal("10"),
        new BigDecimal("100000"),
        List.of(),
        Instant.ofEpochMilli(1762779782500L));
  }
}
