package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FakeExchangeTradingGateway implements ExchangeTradingGateway {

  static final List<AssetBalance> BALANCES =
      List.of(new AssetBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO));

  private final List<MarketOrderRequest> placedOrders = new ArrayList<>();
  private List<AssetBalance> balances = BALANCES;
  private Optional<RuntimeException> orderFailure = Optional.empty();
  private Optional<RuntimeException> balancesFailure = Optional.empty();
  private int balanceRequests;

  @Override
  public ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
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
  public List<AssetBalance> accountBalances() {
    balanceRequests++;
    if (balancesFailure.isPresent()) {
      throw balancesFailure.get();
    }
    return balances;
  }

  List<MarketOrderRequest> placedOrders() {
    return List.copyOf(placedOrders);
  }

  int balanceRequests() {
    return balanceRequests;
  }

  void changeBalances(List<AssetBalance> newBalances) {
    balances = newBalances;
  }

  void failOrders(RuntimeException failure) {
    orderFailure = Optional.of(failure);
  }

  void failBalances(RuntimeException failure) {
    balancesFailure = Optional.of(failure);
  }

  void stopFailingBalances() {
    balancesFailure = Optional.empty();
  }
}
