package com.tihomir.t212.tradingbot.order;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class AccountBalanceCache {

  private final Map<String, CachedAssetBalance> balancesByAsset = new ConcurrentHashMap<>();
  private Instant lastUpdateTime = Instant.EPOCH;
  private boolean loaded;

  synchronized void replaceAll(AccountBalanceSnapshot snapshot) {
    Map<String, CachedAssetBalance> newerThanSnapshot = new HashMap<>();
    balancesByAsset.forEach(
        (asset, cached) -> keepIfNewer(newerThanSnapshot, asset, cached, snapshot.updateTime()));
    balancesByAsset.clear();
    snapshot.balances().forEach(balance -> store(balance, snapshot.updateTime()));
    balancesByAsset.putAll(newerThanSnapshot);
    loaded = true;
  }

  synchronized void applyUpdate(Instant updateTime, List<AssetBalance> changedBalances) {
    changedBalances.stream()
        .filter(balance -> isNewerThanCached(balance.asset(), updateTime))
        .forEach(balance -> store(balance, updateTime));
  }

  synchronized Optional<AccountBalanceSnapshot> snapshot() {
    if (!loaded) {
      return Optional.empty();
    }
    List<AssetBalance> balances =
        balancesByAsset.values().stream()
            .filter(cached -> !cached.empty())
            .map(CachedAssetBalance::balance)
            .sorted(Comparator.comparing(AssetBalance::asset))
            .toList();
    return Optional.of(new AccountBalanceSnapshot(lastUpdateTime, balances));
  }

  synchronized void clear() {
    balancesByAsset.clear();
    lastUpdateTime = Instant.EPOCH;
    loaded = false;
  }

  private boolean isNewerThanCached(String asset, Instant updateTime) {
    CachedAssetBalance cached = balancesByAsset.get(asset);
    return cached == null || !updateTime.isBefore(cached.updateTime());
  }

  private synchronized void store(AssetBalance balance, Instant updateTime) {
    balancesByAsset.put(balance.asset(), new CachedAssetBalance(balance, updateTime));
    if (updateTime.isAfter(lastUpdateTime)) {
      lastUpdateTime = updateTime;
    }
  }

  private static void keepIfNewer(
      Map<String, CachedAssetBalance> newer,
      String asset,
      CachedAssetBalance cached,
      Instant snapshotTime) {
    if (cached.updateTime().isAfter(snapshotTime)) {
      newer.put(asset, cached);
    }
  }
}
