package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.account.value.AccountValuePoint;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountValuePointResponse(Instant time, BigDecimal value) {

  public static AccountValuePointResponse of(AccountValuePoint point) {
    return new AccountValuePointResponse(point.time(), point.value());
  }
}
