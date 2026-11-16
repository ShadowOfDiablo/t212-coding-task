package com.tihomir.t212.tradingbot.order.balance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountBalanceCacheTest {

  private static final Instant SNAPSHOT_TIME = Instant.ofEpochMilli(1_000);
  private static final Instant BEFORE_SNAPSHOT = Instant.ofEpochMilli(500);
  private static final Instant AFTER_SNAPSHOT = Instant.ofEpochMilli(2_000);
  private static final AssetBalance USDT_100 = usdt("100");
  private static final AssetBalance BTC_1 =
      new AssetBalance("BTC", BigDecimal.ONE, BigDecimal.ZERO);

  private final AccountBalanceCache cache = new AccountBalanceCache();

  @Test
  void isEmptyUntilTheFirstSnapshot() {
    cache.applyUpdate(AFTER_SNAPSHOT, List.of(USDT_100));

    assertThat(cache.snapshot()).isEmpty();
  }

  @Test
  void newerUpdateOverwritesOnlyTheChangedAssets() {
    cache.replaceAll(new AccountBalanceSnapshot(SNAPSHOT_TIME, List.of(BTC_1, USDT_100)));

    cache.applyUpdate(AFTER_SNAPSHOT, List.of(usdt("90")));

    assertThat(cache.snapshot().orElseThrow().balances()).containsExactly(BTC_1, usdt("90"));
    assertThat(cache.snapshot().orElseThrow().updateTime()).isEqualTo(AFTER_SNAPSHOT);
  }

  @Test
  void olderUpdateIsIgnored() {
    cache.replaceAll(new AccountBalanceSnapshot(SNAPSHOT_TIME, List.of(USDT_100)));

    cache.applyUpdate(BEFORE_SNAPSHOT, List.of(usdt("90")));

    assertThat(cache.snapshot().orElseThrow().balances()).containsExactly(USDT_100);
  }

  @Test
  void updateNewerThanALaterSnapshotIsKept() {
    cache.applyUpdate(AFTER_SNAPSHOT, List.of(usdt("90")));

    cache.replaceAll(new AccountBalanceSnapshot(SNAPSHOT_TIME, List.of(BTC_1, USDT_100)));

    assertThat(cache.snapshot().orElseThrow().balances()).containsExactly(BTC_1, usdt("90"));
  }

  @Test
  void snapshotDropsAssetsItNoLongerLists() {
    cache.replaceAll(new AccountBalanceSnapshot(SNAPSHOT_TIME, List.of(BTC_1, USDT_100)));

    cache.replaceAll(new AccountBalanceSnapshot(AFTER_SNAPSHOT, List.of(USDT_100)));

    assertThat(cache.snapshot().orElseThrow().balances()).containsExactly(USDT_100);
  }

  @Test
  void emptiedAssetIsLeftOut() {
    cache.replaceAll(new AccountBalanceSnapshot(SNAPSHOT_TIME, List.of(BTC_1, USDT_100)));

    cache.applyUpdate(
        AFTER_SNAPSHOT, List.of(new AssetBalance("BTC", BigDecimal.ZERO, BigDecimal.ZERO)));

    assertThat(cache.snapshot().orElseThrow().balances()).containsExactly(USDT_100);
  }

  @Test
  void clearForgetsEverything() {
    cache.replaceAll(new AccountBalanceSnapshot(SNAPSHOT_TIME, List.of(USDT_100)));

    cache.clear();

    assertThat(cache.snapshot()).isEmpty();
  }

  private static AssetBalance usdt(String free) {
    return new AssetBalance("USDT", new BigDecimal(free), BigDecimal.ZERO);
  }
}
