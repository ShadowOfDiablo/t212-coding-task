package com.tihomir.t212.tradingbot.account;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FakeAccountResetStore implements AccountResetStore {

  private final List<AccountReset> resets = new ArrayList<>();

  @Override
  public synchronized void insertAccountReset(String broker, Instant resetAt) {
    resets.add(new AccountReset(resetAt));
  }

  @Override
  public synchronized AccountResetList getAccountResets(String broker) {
    return new AccountResetList(
        resets.stream().sorted(Comparator.comparing(AccountReset::resetAt)).toList());
  }
}
