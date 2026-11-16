package com.tihomir.t212.tradingbot.account;

import java.util.List;

public record AccountResetList(List<AccountReset> resets) {

  public AccountResetList {
    resets = List.copyOf(resets);
  }
}
