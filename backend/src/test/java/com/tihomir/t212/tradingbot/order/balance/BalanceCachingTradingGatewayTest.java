package com.tihomir.t212.tradingbot.order.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.order.exception.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.exception.OrderStatusUnknownException;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.OrderAmountType;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BalanceCachingTradingGatewayTest {

  private static final Instant LATER = Instant.ofEpochMilli(2_000);
  private static final List<AssetBalance> AFTER_BUY =
      List.of(
          new AssetBalance("BTC", new BigDecimal("0.00012987"), BigDecimal.ZERO),
          new AssetBalance("USDT", new BigDecimal("90"), BigDecimal.ZERO));
  private static final Instant RESET_TIME = Instant.ofEpochMilli(3_000);
  private static final StartingBalances STARTING_BALANCES =
      new StartingBalances(Map.of("USDT", new BigDecimal("100")));
  private static final List<AssetBalance> BACK_TO_START =
      List.of(
          new AssetBalance("BTC", BigDecimal.ZERO, BigDecimal.ZERO),
          new AssetBalance("USDT", new BigDecimal("100.00"), BigDecimal.ZERO));
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

  @Test
  void reportsAResetWhenTheBalancesGoBackToTheStartingOnesButNotAtFirstSight() {
    List<Instant> resets = new ArrayList<>();
    BalanceCachingTradingGateway detecting =
        new BalanceCachingTradingGateway(exchange, STARTING_BALANCES, resets::add);

    detecting.accountBalances();
    detecting.applyBalanceUpdate(LATER, AFTER_BUY);
    detecting.applyBalanceUpdate(RESET_TIME, BACK_TO_START);
    detecting.applyBalanceUpdate(RESET_TIME.plusSeconds(1), BACK_TO_START);

    assertThat(resets).containsExactly(RESET_TIME);
  }

  @Test
  void keepsCachingWhenTheResetCannotBeRecorded() {
    BalanceCachingTradingGateway detecting =
        new BalanceCachingTradingGateway(
            exchange,
            STARTING_BALANCES,
            resetAt -> {
              throw new IllegalStateException("database down");
            });

    detecting.accountBalances();
    detecting.applyBalanceUpdate(LATER, AFTER_BUY);
    detecting.applyBalanceUpdate(RESET_TIME, BACK_TO_START);

    assertThat(detecting.accountBalances().balances())
        .extracting(AssetBalance::asset)
        .containsExactly("USDT");
  }
}
