package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class FakeExchangeTradingGateway implements ExchangeTradingGateway {

  static final List<AssetBalance> BALANCES =
      List.of(new AssetBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO));

  private final List<MarketOrderRequest> placedOrders = new ArrayList<>();

  @Override
  public ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
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
    return BALANCES;
  }

  List<MarketOrderRequest> placedOrders() {
    return List.copyOf(placedOrders);
  }
}
