package com.tihomir.t212.tradingbot.account;

import java.time.Instant;

public interface AccountResetStore {

  void insertAccountReset(String broker, Instant resetAt);

  /** Oldest first. */
  AccountResetList getAccountResets(String broker);
}
