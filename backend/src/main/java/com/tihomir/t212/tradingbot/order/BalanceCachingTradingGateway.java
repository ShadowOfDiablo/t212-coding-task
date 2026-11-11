package com.tihomir.t212.tradingbot.order;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BalanceCachingTradingGateway implements ExchangeTradingGateway {

  private static final Logger log = LoggerFactory.getLogger(BalanceCachingTradingGateway.class);

  private final ExchangeTradingGateway exchangeGateway;
  private Optional<List<AssetBalance>> cachedBalances = Optional.empty();

  public BalanceCachingTradingGateway(ExchangeTradingGateway exchangeGateway) {
    this.exchangeGateway = exchangeGateway;
  }

  @Override
  public ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
    ExecutedOrder order;
    try {
      order = exchangeGateway.placeMarketOrder(request);
    } catch (OrderStatusUnknownException e) {
      clearCachedBalances();
      throw e;
    }
    refreshBalancesAfterOrder(order);
    return order;
  }

  @Override
  public synchronized List<AssetBalance> accountBalances() {
    if (cachedBalances.isEmpty()) {
      cachedBalances = Optional.of(exchangeGateway.accountBalances());
    }
    return cachedBalances.get();
  }

  private synchronized void refreshBalancesAfterOrder(ExecutedOrder order) {
    try {
      cachedBalances = Optional.of(exchangeGateway.accountBalances());
    } catch (RuntimeException e) {
      cachedBalances = Optional.empty();
      log.warn(
          "Could not refresh balances after order {}, reloading them on the next read",
          order.clientOrderId(),
          e);
    }
  }

  private synchronized void clearCachedBalances() {
    cachedBalances = Optional.empty();
  }
}
