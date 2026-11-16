package com.tihomir.t212.tradingbot.order.balance;

import java.time.Instant;

public interface AccountResetListener {

  AccountResetListener NONE = resetAt -> {};

  void onAccountReset(Instant resetAt);
}
