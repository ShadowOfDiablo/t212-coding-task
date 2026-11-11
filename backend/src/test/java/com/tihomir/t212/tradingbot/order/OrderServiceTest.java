package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

  private static final String TRADING_BROKER = "demo";

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final OrderService orderService =
      new OrderService(new BrokerRegistryService(List.of(new FakeBroker(TRADING_BROKER, gateway))));

  @Test
  void sendsTheOrderToTheBrokersGateway() {
    MarketOrderRequest request = buy(FakeBroker.TRADED_TICKER);

    ExecutedOrder order = orderService.placeMarketOrder(TRADING_BROKER, request);

    assertThat(order.ticker()).isEqualTo(FakeBroker.TRADED_TICKER);
    assertThat(gateway.placedOrders()).containsExactly(request);
  }

  @Test
  void refusesAnUnknownBroker() {
    assertThatThrownBy(() -> orderService.placeMarketOrder("unknown", buy("BTCUSDT")))
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
    assertThat(orderService.accountBalances(TRADING_BROKER))
        .isEqualTo(FakeExchangeTradingGateway.BALANCES);
  }

  private static MarketOrderRequest buy(String ticker) {
    return new MarketOrderRequest(
        ticker, OrderSide.BUY, BigDecimal.TEN, OrderAmountType.QUOTE_AMOUNT);
  }
}
