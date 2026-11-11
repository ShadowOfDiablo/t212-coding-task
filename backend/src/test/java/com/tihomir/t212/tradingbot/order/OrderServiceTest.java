package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class OrderServiceTest {

  private static final String TRADING_BROKER = "demo";

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final FakeOrderStore orderStore = new FakeOrderStore();
  private final OrderService orderService =
      new OrderService(
          new BrokerRegistryService(List.of(new FakeBroker(TRADING_BROKER, gateway))),
          orderStore,
          Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));

  @Test
  void sendsTheOrderToTheBrokersGatewayAndMovesItToTheHistory() {
    MarketOrderRequest request = buy(FakeBroker.TRADED_TICKER);

    ExecutedOrder order = orderService.placeMarketOrder(TRADING_BROKER, request);

    assertThat(order.ticker()).isEqualTo(FakeBroker.TRADED_TICKER);
    assertThat(order.clientOrderId()).startsWith("tb-");
    assertThat(gateway.placedOrders()).containsExactly(request);
    assertThat(orderStore.executedOrdersInHistory()).containsExactly(order.clientOrderId());
    assertThat(orderStore.onlyOpenOrder()).isEmpty();
  }

  @Test
  void sendsNothingWhenTheDatabaseIsDown() {
    orderStore.takeDatabaseDown();

    assertThatThrownBy(
            () -> orderService.placeMarketOrder(TRADING_BROKER, buy(FakeBroker.TRADED_TICKER)))
        .isInstanceOf(DataAccessException.class);
    assertThat(gateway.placedOrders()).isEmpty();
  }

  @Test
  void rejectedOrderMovesToTheHistory() {
    gateway.failOrders(new ExchangeRejectedRequestException(-2010, "insufficient balance"));

    assertThatThrownBy(
            () -> orderService.placeMarketOrder(TRADING_BROKER, buy(FakeBroker.TRADED_TICKER)))
        .isInstanceOf(ExchangeRejectedRequestException.class);
    assertThat(orderStore.movedToRejectedOrders()).hasSize(1);
    assertThat(orderStore.onlyOpenOrder()).isEmpty();
  }

  @Test
  void orderWithAnUnknownStatusStaysOpen() {
    gateway.failOrders(new OrderStatusUnknownException("tb-1", "read timed out"));

    assertThatThrownBy(
            () -> orderService.placeMarketOrder(TRADING_BROKER, buy(FakeBroker.TRADED_TICKER)))
        .isInstanceOf(OrderStatusUnknownException.class);
    assertThat(orderStore.onlyOpenOrder().orElseThrow().status())
        .isEqualTo(OpenOrderStatus.UNKNOWN);
  }

  @Test
  void orderThatWasNeverSentIsRemoved() {
    gateway.failOrders(new TradingNotConfiguredException());

    assertThatThrownBy(
            () -> orderService.placeMarketOrder(TRADING_BROKER, buy(FakeBroker.TRADED_TICKER)))
        .isInstanceOf(TradingNotConfiguredException.class);
    assertThat(orderStore.onlyOpenOrder()).isEmpty();
  }

  @Test
  void executedOrderIsReturnedEvenWhenRecordingItFails() {
    orderStore.failMovingToHistory();

    ExecutedOrder order =
        orderService.placeMarketOrder(TRADING_BROKER, buy(FakeBroker.TRADED_TICKER));

    assertThat(order.exchangeStatus()).isEqualTo("FILLED");
    assertThat(orderStore.onlyOpenOrder().orElseThrow().status())
        .isEqualTo(OpenOrderStatus.SENDING);
  }

  @Test
  void refusesAnUnknownBroker() {
    assertThatThrownBy(() -> orderService.placeMarketOrder("unknown", buy("BTCUSDT")))
        .isInstanceOf(BrokerNotFoundException.class);
    assertThatThrownBy(() -> orderService.getRejectedOrders("unknown"))
        .isInstanceOf(BrokerNotFoundException.class);
    assertThatThrownBy(() -> orderService.getOpenOrders("unknown"))
        .isInstanceOf(BrokerNotFoundException.class);
  }

  @Test
  void refusesATickerTheBrokerDoesNotTrade() {
    assertThatThrownBy(() -> orderService.placeMarketOrder(TRADING_BROKER, buy("DOGEUSDT")))
        .isInstanceOf(TickerNotTradedException.class);
    assertThat(gateway.placedOrders()).isEmpty();
    assertThat(orderStore.onlyOpenOrder()).isEmpty();
  }

  @Test
  void readsBalancesFromTheBrokersGateway() {
    assertThat(orderService.accountBalances(TRADING_BROKER))
        .isEqualTo(FakeExchangeTradingGateway.BALANCES);
  }

  private static MarketOrderRequest buy(String ticker) {
    return new MarketOrderRequest(
        ticker, OrderSide.BUY, BigDecimal.TEN, OrderAmountType.QUOTE_AMOUNT);
  }
}
