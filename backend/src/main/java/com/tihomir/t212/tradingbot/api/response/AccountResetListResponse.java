package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.account.AccountReset;
import com.tihomir.t212.tradingbot.account.AccountResetList;
import java.time.Instant;
import java.util.List;

public record AccountResetListResponse(List<Instant> resetTimes) {

  public static AccountResetListResponse of(AccountResetList resets) {
    return new AccountResetListResponse(
        resets.resets().stream().map(AccountReset::resetAt).toList());
  }
}
