package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.exception.OrderStatusUnknownException;
import com.tihomir.t212.tradingbot.order.exception.TickerNotTradedException;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.OrderAmountType;
import com.tihomir.t212.tradingbot.order.model.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import com.tihomir.t212.tradingbot.order.model.RejectedOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class OrderServiceTest {

  private static final String TRADING_BROKER = "demo";
  private static final Instant NOW = Instant.ofEpochMilli(1_000);

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final FakeOrderStore orderStore = new FakeOrderStore();
  private final OrderService orderService =
      new OrderService(
          new BrokerRegistryService(List.of(new FakeBroker(TRADING_BROKER, gateway))),
          orderStore,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void executedOrderIsStoredUnderTheExchangeOrderId() {
    MarketOrderRequest request = buy(FakeBroker.TRADED_TICKER);

    ExecutedOrder order = orderService.placeMarketOrder(TRADING_BROKER, request);

    assertThat(gateway.placedOrders()).containsExactly(request);
    OrderHistoryEntry stored = orderService.getOrderHistory(TRADING_BROKER).orders().getFirst();
    assertThat(stored.exchangeOrderId()).isEqualTo(FakeExchangeTradingGateway.EXCHANGE_ORDER_ID);
    assertThat(stored.broker()).isEqualTo(TRADING_BROKER);
    assertThat(stored.amountType()).isEqualTo(OrderAmountType.QUOTE_AMOUNT);
    assertThat(stored.createdAt()).isEqualTo(NOW);
    assertThat(stored.finalStatus()).isEqualTo(order.exchangeStatus());
  }

  @Test
  void executedOrderIsReturnedEvenWhenStoringItFails() {
    orderStore.takeDatabaseDown();

    ExecutedOrder order =
        orderService.placeMarketOrder(TRADING_BROKER, buy(FakeBroker.TRADED_TICKER));

    assertThat(order.exchangeStatus()).isEqualTo("FILLED");
  }

  @Test
  void rejectedOrderIsStoredWithTheExchangeError() {
    gateway.failOrders(new ExchangeRejectedRequestException(-2010, "insufficient balance"));

    assertThatThrownBy(
            () -> orderService.placeMarketOrder(TRADING_BROKER, buy(FakeBroker.TRADED_TICKER)))
        .isInstanceOf(ExchangeRejectedRequestException.class);

    RejectedOrder rejected =
        orderService.getRejectedOrders(TRADING_BROKER).rejectedOrders().getFirst();
    assertThat(rejected.errorCode()).isEqualTo(-2010);
    assertThat(rejected.errorMessage()).isEqualTo("insufficient balance");
    assertThat(rejected.rejectedAt()).isEqualTo(NOW);
    assertThat(orderService.getOrderHistory(TRADING_BROKER).orders()).isEmpty();
  }

  @Test
  void orderWithAnUnknownStatusIsNotStored() {
    gateway.failOrders(new OrderStatusUnknownException("tb-1", "read timed out"));

    assertThatThrownBy(
            () -> orderService.placeMarketOrder(TRADING_BROKER, buy(FakeBroker.TRADED_TICKER)))
        .isInstanceOf(OrderStatusUnknownException.class);
    assertThat(orderService.getOrderHistory(TRADING_BROKER).orders()).isEmpty();
    assertThat(orderService.getRejectedOrders(TRADING_BROKER).rejectedOrders()).isEmpty();
  }

  @Test
  void readsFailWhenTheDatabaseIsDown() {
    orderStore.takeDatabaseDown();

    assertThatThrownBy(() -> orderService.getOrderHistory(TRADING_BROKER))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void refusesAnUnknownBroker() {
    assertThatThrownBy(() -> orderService.placeMarketOrder("unknown", buy("BTCUSDT")))
        .isInstanceOf(BrokerNotFoundException.class);
    assertThatThrownBy(() -> orderService.getOpenOrders("unknown"))
        .isInstanceOf(BrokerNotFoundException.class);
    assertThatThrownBy(() -> orderService.getRejectedOrders("unknown"))
        .isInstanceOf(BrokerNotFoundException.class);
  }

  @Test
  void refusesATickerTheBrokerDoesNotTrade() {
    assertThatThrownBy(() -> orderService.placeMarketOrder(TRADING_BROKER, buy("DOGEUSDT")))
        .isInstanceOf(TickerNotTradedException.class);
    assertThat(gateway.placedOrders()).isEmpty();
  }

  @Test
  void readsBalancesFromTheBrokersGateway() {
    assertThat(orderService.getAccountBalances(TRADING_BROKER).balances())
        .isEqualTo(FakeExchangeTradingGateway.BALANCES);
  }

  private static MarketOrderRequest buy(String ticker) {
    return new MarketOrderRequest(
        ticker, OrderSide.BUY, BigDecimal.TEN, OrderAmountType.QUOTE_AMOUNT);
  }
}
