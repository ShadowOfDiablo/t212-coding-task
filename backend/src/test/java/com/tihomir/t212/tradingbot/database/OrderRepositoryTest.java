package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OpenOrder;
import com.tihomir.t212.tradingbot.order.OpenOrderStatus;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;
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
  private static final String CLIENT_ORDER_ID = "tb-1";
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
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTables() {
    jdbcClient.sql("DELETE FROM order_fill").update();
    jdbcClient.sql("DELETE FROM order_history").update();
    jdbcClient.sql("DELETE FROM open_order").update();
  }

  @Test
  void openOrderIsListedUntilItIsSettled() {
    orderRepository.insertOpenOrder(sendingBuy());

    assertThat(orderRepository.openOrders(BROKER)).containsExactly(sendingBuy());
    assertThat(orderRepository.openOrders("other")).isEmpty();
  }

  @Test
  void unknownStatusIsRecorded() {
    orderRepository.insertOpenOrder(sendingBuy());

    orderRepository.markOpenOrderStatus(CLIENT_ORDER_ID, OpenOrderStatus.UNKNOWN, EXECUTED_AT);

    OpenOrder order = orderRepository.openOrders(BROKER).getFirst();
    assertThat(order.status()).isEqualTo(OpenOrderStatus.UNKNOWN);
    assertThat(order.updatedAt()).isEqualTo(EXECUTED_AT);
  }

  @Test
  void executedOrderMovesToTheHistoryWithItsFills() {
    orderRepository.insertOpenOrder(sendingBuy());

    orderRepository.settleExecutedOrder(executedBuy(List.of(FIRST_FILL, SECOND_FILL)));

    assertThat(orderRepository.openOrders(BROKER)).isEmpty();
    OrderHistoryEntry entry = orderRepository.orderHistory(BROKER).getFirst();
    assertThat(entry.clientOrderId()).isEqualTo(CLIENT_ORDER_ID);
    assertThat(entry.exchangeOrderId()).contains(28L);
    assertThat(entry.finalStatus()).isEqualTo("FILLED");
    assertThat(entry.executedQuantity()).isEqualByComparingTo("0.0001");
    assertThat(entry.createdAt()).isEqualTo(CREATED_AT);
    assertThat(entry.closedAt()).isEqualTo(EXECUTED_AT);
    assertThat(entry.fills()).containsExactly(FIRST_FILL, SECOND_FILL);
  }

  @Test
  void settlingTwiceChangesNothing() {
    orderRepository.insertOpenOrder(sendingBuy());
    ExecutedOrder order = executedBuy(List.of(FIRST_FILL));

    orderRepository.settleExecutedOrder(order);
    orderRepository.settleExecutedOrder(order);

    assertThat(orderRepository.orderHistory(BROKER)).hasSize(1);
    assertThat(orderRepository.orderHistory(BROKER).getFirst().fills()).hasSize(1);
  }

  @Test
  void rejectedOrderMovesToTheHistoryWithTheExchangeError() {
    orderRepository.insertOpenOrder(sendingBuy());

    orderRepository.settleRejectedOrder(
        CLIENT_ORDER_ID,
        new ExchangeRejectedRequestException(-2010, "insufficient balance"),
        EXECUTED_AT);

    assertThat(orderRepository.openOrders(BROKER)).isEmpty();
    OrderHistoryEntry entry = orderRepository.orderHistory(BROKER).getFirst();
    assertThat(entry.finalStatus()).isEqualTo("REJECTED");
    assertThat(entry.errorCode()).contains(-2010);
    assertThat(entry.errorMessage()).contains("insufficient balance");
    assertThat(entry.exchangeOrderId()).isEmpty();
    assertThat(entry.fills()).isEmpty();
  }

  @Test
  void failedSettlementLeavesTheOrderOpenAndTheHistoryUntouched() {
    orderRepository.insertOpenOrder(sendingBuy());
    OrderFill fillWithoutFeeAsset =
        new OrderFill(9, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null);

    assertThatThrownBy(() ->
            orderRepository.settleExecutedOrder(
                executedBuy(List.of(FIRST_FILL, fillWithoutFeeAsset))))
        .isInstanceOf(DataAccessException.class);

    assertThat(orderRepository.openOrders(BROKER)).hasSize(1);
    assertThat(orderRepository.orderHistory(BROKER)).isEmpty();
    assertThat(jdbcClient.sql("SELECT count(*) FROM order_fill").query(Long.class).single())
        .isZero();
  }

  private static OpenOrder sendingBuy() {
    return new OpenOrder(
        CLIENT_ORDER_ID,
        BROKER,
        "BTCUSDT",
        OrderSide.BUY,
        OpenOrder.MARKET_ORDER_TYPE,
        new BigDecimal("10"),
        OrderAmountType.QUOTE_AMOUNT,
        OpenOrderStatus.SENDING,
        CREATED_AT,
        CREATED_AT);
  }

  private static ExecutedOrder executedBuy(List<OrderFill> fills) {
    return new ExecutedOrder(
        28,
        CLIENT_ORDER_ID,
        "BTCUSDT",
        OrderSide.BUY,
        "FILLED",
        new BigDecimal("0.0001"),
        new BigDecimal("10"),
        new BigDecimal("100000"),
        fills,
        EXECUTED_AT);
  }
}
