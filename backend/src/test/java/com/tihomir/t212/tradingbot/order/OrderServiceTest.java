package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

  private static final String TRADING_BROKER = "demo";
  private static final String MARKET_DATA_ONLY_BROKER = "watch-only";

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final OrderService orderService =
      new OrderService(
          new BrokerRegistryService(
              List.of(
                  new FakeBroker(TRADING_BROKER, Optional.of(gateway)),
                  new FakeBroker(MARKET_DATA_ONLY_BROKER, Optional.empty()))));

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
  void refusesABrokerWithoutTradingKeys() {
    assertThatThrownBy(() ->
            orderService.placeMarketOrder(MARKET_DATA_ONLY_BROKER, buy(FakeBroker.TRADED_TICKER)))
        .isInstanceOf(TradingNotConfiguredException.class);
    assertThatThrownBy(() -> orderService.accountBalances(MARKET_DATA_ONLY_BROKER))
        .isInstanceOf(TradingNotConfiguredException.class);
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
