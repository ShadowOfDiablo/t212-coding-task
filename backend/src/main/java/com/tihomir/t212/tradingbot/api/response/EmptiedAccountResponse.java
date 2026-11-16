package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.account.EmptiedAccount;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import java.util.List;

public record EmptiedAccountResponse(
    List<AssetBalance> soldBalances, List<AssetBalance> keptBalances, int closedPositionCount) {

  public static EmptiedAccountResponse of(EmptiedAccount emptied) {
    return new EmptiedAccountResponse(
        emptied.soldBalances(), emptied.keptBalances(), emptied.closedPositionCount());
  }
}
