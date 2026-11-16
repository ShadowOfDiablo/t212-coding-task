package com.tihomir.t212.tradingbot.order.balance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/** The balances an exchange account holds right after a reset, free and locked together. */
public record StartingBalances(Map<String, BigDecimal> quantityByAsset) {

  public StartingBalances {
    quantityByAsset = Map.copyOf(quantityByAsset);
  }

  /** True when the account holds exactly these balances and nothing else. */
  public boolean matches(AccountBalanceSnapshot snapshot) {
    Map<String, BigDecimal> held =
        snapshot.balances().stream()
            .filter(balance -> balance.free().add(balance.locked()).signum() > 0)
            .collect(
                Collectors.toMap(
                    AssetBalance::asset, balance -> balance.free().add(balance.locked())));
    return held.keySet().equals(quantityByAsset.keySet())
        && held.entrySet().stream()
            .allMatch(
                entry -> entry.getValue().compareTo(quantityByAsset.get(entry.getKey())) == 0);
  }
}
