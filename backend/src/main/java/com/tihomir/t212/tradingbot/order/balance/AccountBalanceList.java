package com.tihomir.t212.tradingbot.order.balance;

import java.util.List;

public record AccountBalanceList(List<AssetBalance> balances) {

  public AccountBalanceList {
    balances = List.copyOf(balances);
  }
}
