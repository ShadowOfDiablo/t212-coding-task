package com.tihomir.t212.tradingbot.order.balance;

import com.tihomir.t212.tradingbot.order.exception.OrderStatusUnknownException;
import com.tihomir.t212.tradingbot.order.gateway.ExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.TickerTradingRules;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches the account balances. When they change to exactly the starting balances from anything
 * else, the account was reset on the exchange, and the reset listener is told.
 */
public final class BalanceCachingTradingGateway implements ExchangeTradingGateway {

  private static final Logger log = LoggerFactory.getLogger(BalanceCachingTradingGateway.class);

  private final ExchangeTradingGateway exchangeGateway;
  private final StartingBalances startingBalances;
  private final AccountResetListener resetListener;
  private final AccountBalanceCache cache = new AccountBalanceCache();
  private boolean balancesSeen;
  private boolean atStartingBalances;

  public BalanceCachingTradingGateway(ExchangeTradingGateway exchangeGateway) {
    this(exchangeGateway, new StartingBalances(Map.of()), AccountResetListener.NONE);
  }

  public BalanceCachingTradingGateway(
      ExchangeTradingGateway exchangeGateway,
      StartingBalances startingBalances,
      AccountResetListener resetListener) {
    this.exchangeGateway = exchangeGateway;
    this.startingBalances = startingBalances;
    this.resetListener = resetListener;
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

  @Override
  public TickerTradingRules tradingRules(String ticker) {
    return exchangeGateway.tradingRules(ticker);
  }

  public void synchronizeBalances() {
    cache.replaceAll(exchangeGateway.accountBalances());
    detectReset();
  }

  public void applyBalanceUpdate(Instant updateTime, List<AssetBalance> changedBalances) {
    cache.applyUpdate(updateTime, changedBalances);
    detectReset();
  }

  /** The first balances seen are never a reset: what came before them is unknown. */
  private synchronized void detectReset() {
    Optional<AccountBalanceSnapshot> snapshot = cache.snapshot();
    if (snapshot.isEmpty()) {
      return;
    }
    boolean starting = startingBalances.matches(snapshot.get());
    if (balancesSeen && !atStartingBalances && starting) {
      notifyReset(snapshot.get().updateTime());
    }
    balancesSeen = true;
    atStartingBalances = starting;
  }

  private void notifyReset(Instant resetAt) {
    try {
      resetListener.onAccountReset(resetAt);
    } catch (RuntimeException e) {
      log.error("The account reset at {} could not be recorded", resetAt, e);
    }
  }
}
