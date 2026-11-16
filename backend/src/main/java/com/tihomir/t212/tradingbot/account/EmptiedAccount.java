package com.tihomir.t212.tradingbot.account;

import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import java.util.List;

/**
 * What emptying the account did: the balances sold to USDT, the ones that stayed (too small, not
 * followed, or refused by the exchange) and how many open positions were closed.
 */
public record EmptiedAccount(
    List<AssetBalance> soldBalances, List<AssetBalance> keptBalances, int closedPositionCount) {

  public EmptiedAccount {
    soldBalances = List.copyOf(soldBalances);
    keptBalances = List.copyOf(keptBalances);
  }
}
