package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import com.tihomir.t212.tradingbot.order.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.OpenOrder;
import com.tihomir.t212.tradingbot.order.OpenOrderStatus;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.OrderSide;
import com.tihomir.t212.tradingbot.order.RejectedOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final String BROKER = "binance";
  private static final long EXCHANGE_ORDER_ID = 28;
  private static final long OPEN_ORDER_ID = 30;
  private static final String FILLED = "FILLED";
  private static final String UNKNOWN_STATUS = "PENDING_NEW";
  private static final BigDecimal OPEN_ORDER_AMOUNT = new BigDecimal("0.0001");
  private static final Instant CREATED_AT = Instant.parse("2026-11-11T10:00:00Z");
  private static final Instant EXECUTED_AT = Instant.parse("2026-11-11T10:00:01Z");
  private static final OrderFill FIRST_FILL =
      new OrderFill(
          7,
          new BigDecimal("100000.00000000"),
          new BigDecimal("0.00006000"),
          new BigDecimal("0.00000006"),
          "BTC");
  private static final OrderFill SECOND_FILL =
      new OrderFill(
          8,
          new BigDecimal("100001.00000000"),
          new BigDecimal("0.00004000"),
          new BigDecimal("0.00000004"),
          "BTC");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired OrderRepository orderRepository;
  @Autowired CandleRepository candleRepository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTables() {
    jdbcClient.sql("DELETE FROM order_fill").update();
    jdbcClient.sql("DELETE FROM orders").update();
  }

  @Test
  void executedOrderIsStoredWithItsFillsUnderTheExchangeOrderId() {
    orderRepository.insertExecutedOrder(executedBuy(List.of(FIRST_FILL, SECOND_FILL)));

    OrderHistoryEntry entry = orderRepository.getOrderHistory(BROKER).orders().getFirst();
    assertThat(entry).isEqualTo(executedBuy(List.of(FIRST_FILL, SECOND_FILL)));
    assertThat(orderRepository.getOrderHistory("other").orders()).isEmpty();
  }

  @Test
  void insertingTheSameExecutedOrderTwiceChangesNothing() {
    orderRepository.insertExecutedOrder(executedBuy(List.of(FIRST_FILL)));
    orderRepository.insertExecutedOrder(executedBuy(List.of(FIRST_FILL)));

    assertThat(orderRepository.getOrderHistory(BROKER).orders()).hasSize(1);
    assertThat(orderRepository.getOrderHistory(BROKER).orders().getFirst().fills()).hasSize(1);
  }

  @Test
  void failedInsertLeavesNothingBehind() {
    OrderFill fillWithoutFeeAsset =
        new OrderFill(9, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null);

    assertThatThrownBy(() ->
            orderRepository.insertExecutedOrder(
                executedBuy(List.of(FIRST_FILL, fillWithoutFeeAsset))))
        .isInstanceOf(DataAccessException.class);

    assertThat(orderRepository.getOrderHistory(BROKER).orders()).isEmpty();
    assertThat(jdbcClient.sql("SELECT count(*) FROM order_fill").query(Long.class).single())
        .isZero();
  }

  @Test
  void rejectedOrderIsStoredWithTheExchangeError() {
    RejectedOrder rejected =
        new RejectedOrder(
            BROKER,
            "BTCUSDT",
            OrderSide.SELL,
            MarketOrderRequest.ORDER_TYPE,
            new BigDecimal("0.00013"),
            OrderAmountType.BASE_QUANTITY,
            -2010,
            "insufficient balance",
            EXECUTED_AT);

    orderRepository.insertRejectedOrder(rejected);
    orderRepository.insertRejectedOrder(rejected);

    assertThat(orderRepository.getRejectedOrders(BROKER).rejectedOrders())
        .containsExactly(rejected, rejected);
    assertThat(orderRepository.getOrderHistory(BROKER).orders()).isEmpty();
  }

  @Test
  void refusesAnOrderWithAnUnknownStatus() {
    assertThatThrownBy(
            () -> orderRepository.insertExecutedOrder(executedBuy(UNKNOWN_STATUS, List.of())))
        .isInstanceOf(DataAccessException.class);

    assertThat(orderRepository.getOrderHistory(BROKER).orders()).isEmpty();
  }

  @Test
  void openOrdersAreKeyedByTheExchangeOrderId() {
    jdbcClient
        .sql(
            """
            INSERT INTO orders (ticker_id, exchange_order_id, side, order_type, amount_type, status,
                                amount, created_at, updated_at)
            VALUES (?, ?, 'SELL', 'MARKET', 'BASE_QUANTITY', 'NEW', ?, ?, ?)
            """)
        .param(candleRepository.insertTicker(BROKER, "BTCUSDT"))
        .param(OPEN_ORDER_ID)
        .param(OPEN_ORDER_AMOUNT)
        .param(CREATED_AT.atOffset(ZoneOffset.UTC))
        .param(CREATED_AT.atOffset(ZoneOffset.UTC))
        .update();

    OpenOrder order = orderRepository.getOpenOrders(BROKER).openOrders().getFirst();
    assertThat(order.exchangeOrderId()).isEqualTo(OPEN_ORDER_ID);
    assertThat(order.status()).isEqualTo(OpenOrderStatus.NEW);
    assertThat(order.createdAt()).isEqualTo(CREATED_AT);
  }

  private static OrderHistoryEntry executedBuy(List<OrderFill> fills) {
    return executedBuy(FILLED, fills);
  }

  private static OrderHistoryEntry executedBuy(String finalStatus, List<OrderFill> fills) {
    return new OrderHistoryEntry(
        EXCHANGE_ORDER_ID,
        BROKER,
        "BTCUSDT",
        OrderSide.BUY,
        MarketOrderRequest.ORDER_TYPE,
        new BigDecimal("10"),
        OrderAmountType.QUOTE_AMOUNT,
        finalStatus,
        new BigDecimal("0.0001"),
        new BigDecimal("10"),
        new BigDecimal("100000"),
        CREATED_AT,
        EXECUTED_AT,
        fills);
  }
}
