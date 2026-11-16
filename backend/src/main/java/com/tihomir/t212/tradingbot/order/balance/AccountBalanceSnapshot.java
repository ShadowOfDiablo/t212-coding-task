package com.tihomir.t212.tradingbot.order.balance;

import java.time.Instant;
import java.util.List;

public record AccountBalanceSnapshot(Instant updateTime, List<AssetBalance> balances) {

  public AccountBalanceSnapshot {
    balances = List.copyOf(balances);
  }
}
