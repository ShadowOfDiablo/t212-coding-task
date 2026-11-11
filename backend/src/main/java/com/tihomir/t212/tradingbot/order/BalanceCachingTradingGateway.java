package com.tihomir.t212.tradingbot.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class BalanceCachingTradingGateway implements ExchangeTradingGateway {

  private final ExchangeTradingGateway exchangeGateway;
  private final AccountBalanceCache cache = new AccountBalanceCache();

  public BalanceCachingTradingGateway(ExchangeTradingGateway exchangeGateway) {
    this.exchangeGateway = exchangeGateway;
  }

  @Override
  public ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
    try {
      return exchangeGateway.placeMarketOrder(request);
    } catch (OrderStatusUnknownException e) {
      cache.clear();
      throw e;
    }
  }

  @Override
  public AccountBalanceSnapshot accountBalances() {
    Optional<AccountBalanceSnapshot> cached = cache.snapshot();
    if (cached.isPresent()) {
      return cached.get();
    }
    synchronizeBalances();
    return cache.snapshot().orElseThrow();
  }

  public void synchronizeBalances() {
    cache.replaceAll(exchangeGateway.accountBalances());
  }

  public void applyBalanceUpdate(Instant updateTime, List<AssetBalance> changedBalances) {
    cache.applyUpdate(updateTime, changedBalances);
  }
}
