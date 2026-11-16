package com.tihomir.t212.tradingbot.order.gateway;

import com.tihomir.t212.tradingbot.order.balance.AccountBalanceSnapshot;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.TickerTradingRules;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FakeExchangeTradingGateway implements ExchangeTradingGateway {

  public static final long EXCHANGE_ORDER_ID = 28;
  public static final Instant SNAPSHOT_TIME = Instant.ofEpochMilli(1_000);
  public static final TickerTradingRules TRADING_RULES =
      new TickerTradingRules(new BigDecimal("0.00000001"), new BigDecimal("5"));
  public static final List<AssetBalance> BALANCES =
      List.of(new AssetBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO));

  private final List<MarketOrderRequest> placedOrders = new ArrayList<>();
  private AccountBalanceSnapshot snapshot = new AccountBalanceSnapshot(SNAPSHOT_TIME, BALANCES);
  private Optional<RuntimeException> orderFailure = Optional.empty();
  private Optional<RuntimeException> balancesFailure = Optional.empty();
  private Optional<RuntimeException> tradingRulesFailure = Optional.empty();
  private int remainingBalanceFailures;
  private int balanceRequests;

  @Override
  public synchronized ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
    if (orderFailure.isPresent()) {
      throw orderFailure.get();
    }
    placedOrders.add(request);
    return new ExecutedOrder(
        EXCHANGE_ORDER_ID,
        "tb-fake",
        request.ticker(),
        request.side(),
        "FILLED",
        request.amount(),
        request.amount(),
        BigDecimal.ONE,
        List.of(),
        Instant.EPOCH);
  }

  @Override
  public synchronized TickerTradingRules tradingRules(String ticker) {
    if (tradingRulesFailure.isPresent()) {
      throw tradingRulesFailure.get();
    }
    return TRADING_RULES;
  }

  public synchronized void failTradingRules(RuntimeException failure) {
    tradingRulesFailure = Optional.of(failure);
  }

  @Override
  public synchronized AccountBalanceSnapshot accountBalances() {
    balanceRequests++;
    if (remainingBalanceFailures > 0 && balancesFailure.isPresent()) {
      remainingBalanceFailures--;
      throw balancesFailure.get();
    }
    return snapshot;
  }

  public synchronized int balanceRequests() {
    return balanceRequests;
  }

  public synchronized void changeBalances(Instant updateTime, List<AssetBalance> newBalances) {
    snapshot = new AccountBalanceSnapshot(updateTime, newBalances);
  }

  public synchronized void failBalanceRequests(RuntimeException failure, int times) {
    balancesFailure = Optional.of(failure);
    remainingBalanceFailures = times;
  }

  public synchronized List<MarketOrderRequest> placedOrders() {
    return List.copyOf(placedOrders);
  }

  public synchronized void failOrders(RuntimeException failure) {
    orderFailure = Optional.of(failure);
  }
}
