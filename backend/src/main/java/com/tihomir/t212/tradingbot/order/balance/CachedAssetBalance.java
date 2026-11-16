package com.tihomir.t212.tradingbot.order.balance;

import java.time.Instant;

record CachedAssetBalance(AssetBalance balance, Instant updateTime) {

  boolean empty() {
    return balance.free().signum() == 0 && balance.locked().signum() == 0;
  }
}
