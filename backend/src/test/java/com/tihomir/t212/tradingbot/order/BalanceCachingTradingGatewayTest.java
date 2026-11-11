package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceCachingTradingGatewayTest {

  private static final Instant LATER = Instant.ofEpochMilli(2_000);
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

    assertThat(gateway.accountBalances().balances()).isEqualTo(FakeExchangeTradingGateway.BALANCES);
    assertThat(exchange.balanceRequests()).isEqualTo(1);
  }

  @Test
  void executedOrderSendsNoBalanceRequest() {
    gateway.accountBalances();

    gateway.placeMarketOrder(BUY);
    gateway.accountBalances();

    assertThat(exchange.balanceRequests()).isEqualTo(1);
  }

  @Test
  void streamedBalanceUpdateChangesTheCache() {
    gateway.accountBalances();

    gateway.applyBalanceUpdate(LATER, AFTER_BUY);

    assertThat(gateway.accountBalances().balances()).isEqualTo(AFTER_BUY);
    assertThat(exchange.balanceRequests()).isEqualTo(1);
  }

  @Test
  void synchronizingReplacesTheCacheWithTheExchangesBalances() {
    gateway.accountBalances();
    exchange.changeBalances(LATER, AFTER_BUY);

    gateway.synchronizeBalances();

    assertThat(gateway.accountBalances().balances()).isEqualTo(AFTER_BUY);
  }

  @Test
  void reloadsBalancesAfterAnOrderWithAnUnknownStatus() {
    gateway.accountBalances();
    exchange.failOrders(new OrderStatusUnknownException("tb-1", "read timed out"));
    exchange.changeBalances(LATER, AFTER_BUY);

    assertThatThrownBy(() -> gateway.placeMarketOrder(BUY))
        .isInstanceOf(OrderStatusUnknownException.class);

    assertThat(gateway.accountBalances().balances()).isEqualTo(AFTER_BUY);
    assertThat(exchange.balanceRequests()).isEqualTo(2);
  }

  @Test
  void failedFirstReadIsRetriedOnTheNextRead() {
    exchange.failBalanceRequests(new ExchangeUnavailableException("connect timed out"), 1);

    assertThatThrownBy(gateway::accountBalances).isInstanceOf(ExchangeUnavailableException.class);

    assertThat(gateway.accountBalances().balances()).isEqualTo(FakeExchangeTradingGateway.BALANCES);
  }
}
