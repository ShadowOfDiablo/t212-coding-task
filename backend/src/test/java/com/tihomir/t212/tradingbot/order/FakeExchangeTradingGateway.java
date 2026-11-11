package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FakeExchangeTradingGateway implements ExchangeTradingGateway {

  public static final Instant SNAPSHOT_TIME = Instant.ofEpochMilli(1_000);
  public static final List<AssetBalance> BALANCES =
      List.of(new AssetBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO));

  private final List<MarketOrderRequest> placedOrders = new ArrayList<>();
  private AccountBalanceSnapshot snapshot = new AccountBalanceSnapshot(SNAPSHOT_TIME, BALANCES);
  private Optional<RuntimeException> orderFailure = Optional.empty();
  private Optional<RuntimeException> balancesFailure = Optional.empty();
  private int remainingBalanceFailures;
  private int balanceRequests;

  @Override
  public synchronized ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
    if (orderFailure.isPresent()) {
      throw orderFailure.get();
    }
    placedOrders.add(request);
    return new ExecutedOrder(
        1,
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

  synchronized List<MarketOrderRequest> placedOrders() {
    return List.copyOf(placedOrders);
  }

  synchronized void failOrders(RuntimeException failure) {
    orderFailure = Optional.of(failure);
  }
}
