package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.account.value.AccountValuePointList;
import java.util.List;

public record AccountValuePointListResponse(List<AccountValuePointResponse> points) {

  public static AccountValuePointListResponse of(AccountValuePointList list) {
    return new AccountValuePointListResponse(
        list.points().stream().map(AccountValuePointResponse::of).toList());
  }
}
