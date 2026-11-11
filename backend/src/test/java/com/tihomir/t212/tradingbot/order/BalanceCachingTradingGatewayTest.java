package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceCachingTradingGatewayTest {

  private static final List<AssetBalance> AFTER_BUY =
      List.of(
          new AssetBalance("BTC", new BigDecimal("0.00012987"), BigDecimal.ZERO),
          new AssetBalance("USDT", new BigDecimal("90"), BigDecimal.ZERO));
  private static final MarketOrderRequest BUY =
      new MarketOrderRequest(
          "BTCUSDT", OrderSide.BUY, BigDecimal.TEN, OrderAmountType.QUOTE_AMOUNT);

  private final FakeExchangeTradingGateway exchange = new FakeExchangeTradingGateway();
  private final BalanceCachingTradingGateway gateway = new BalanceCachingTradingGateway(exchange);

  @Test
  void readsBalancesFromTheExchangeOnlyOnce() {
    gateway.accountBalances();
    gateway.accountBalances();

    assertThat(gateway.accountBalances()).isEqualTo(FakeExchangeTradingGateway.BALANCES);
    assertThat(exchange.balanceRequests()).isEqualTo(1);
  }

  @Test
  void replacesTheCacheWithTheExchangesBalancesAfterAnExecutedOrder() {
    gateway.accountBalances();
    exchange.changeBalances(AFTER_BUY);

    gateway.placeMarketOrder(BUY);

    assertThat(gateway.accountBalances()).isEqualTo(AFTER_BUY);
    assertThat(exchange.balanceRequests()).isEqualTo(2);
  }

  @Test
  void keepsTheCacheWhenTheExchangeRejectsTheOrder() {
    gateway.accountBalances();
    exchange.failOrders(new ExchangeRejectedRequestException(-2010, "insufficient balance"));

    assertThatThrownBy(() -> gateway.placeMarketOrder(BUY))
        .isInstanceOf(ExchangeRejectedRequestException.class);

    gateway.accountBalances();
    assertThat(exchange.balanceRequests()).isEqualTo(1);
  }

  @Test
  void reloadsBalancesAfterAnOrderWithAnUnknownStatus() {
    gateway.accountBalances();
    exchange.failOrders(new OrderStatusUnknownException("tb-1", "read timed out"));
    exchange.changeBalances(AFTER_BUY);

    assertThatThrownBy(() -> gateway.placeMarketOrder(BUY))
        .isInstanceOf(OrderStatusUnknownException.class);

    assertThat(gateway.accountBalances()).isEqualTo(AFTER_BUY);
    assertThat(exchange.balanceRequests()).isEqualTo(2);
  }

  @Test
  void executedOrderIsReturnedEvenWhenTheRefreshFails() {
    gateway.accountBalances();
    exchange.failBalances(new ExchangeUnavailableException("connect timed out"));

    ExecutedOrder order = gateway.placeMarketOrder(BUY);

    assertThat(order.ticker()).isEqualTo("BTCUSDT");
    exchange.stopFailingBalances();
    exchange.changeBalances(AFTER_BUY);
    assertThat(gateway.accountBalances()).isEqualTo(AFTER_BUY);
  }

  @Test
  void failedFirstReadIsRetriedOnTheNextRead() {
    exchange.failBalances(new ExchangeUnavailableException("connect timed out"));

    assertThatThrownBy(gateway::accountBalances).isInstanceOf(ExchangeUnavailableException.class);

    exchange.stopFailingBalances();
    assertThat(gateway.accountBalances()).isEqualTo(FakeExchangeTradingGateway.BALANCES);
  }
}
