package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AccountBalanceSynchronizerTest {

  private static final Duration SYNC_INTERVAL = Duration.ofMillis(800);
  private static final Duration RETRY_DELAY = Duration.ofMillis(50);
  private static final Duration WELL_BEFORE_THE_NEXT_SYNC = Duration.ofMillis(400);
  private static final Duration A_FEW_SYNCS = Duration.ofSeconds(3);
  private static final ExchangeUnavailableException CONNECT_TIMEOUT =
      new ExchangeUnavailableException("connect timed out");

  private final FakeExchangeTradingGateway exchange = new FakeExchangeTradingGateway();
  private final BalanceCachingTradingGateway gateway = new BalanceCachingTradingGateway(exchange);
  private final AccountBalanceSynchronizer synchronizer =
      new AccountBalanceSynchronizer(gateway, SYNC_INTERVAL, RETRY_DELAY);

  @AfterEach
  void stopSynchronizer() {
    synchronizer.close();
  }

  @Test
  void successfulSyncLoadsTheCacheWithoutARetry() throws InterruptedException {
    synchronizer.synchronizeSoon();

    await().atMost(WELL_BEFORE_THE_NEXT_SYNC).until(() -> exchange.balanceRequests() == 1);
    Thread.sleep(RETRY_DELAY.multipliedBy(2).toMillis());
    assertThat(exchange.balanceRequests()).isEqualTo(1);
    assertThat(gateway.accountBalances().balances()).isEqualTo(FakeExchangeTradingGateway.BALANCES);
  }

  @Test
  void failedSyncIsRetriedOnce() {
    exchange.failBalanceRequests(CONNECT_TIMEOUT, 1);

    synchronizer.synchronizeSoon();

    await().atMost(WELL_BEFORE_THE_NEXT_SYNC).until(() -> exchange.balanceRequests() == 2);
    assertThat(gateway.accountBalances().balances()).isEqualTo(FakeExchangeTradingGateway.BALANCES);
    assertThat(exchange.balanceRequests()).isEqualTo(2);
  }

  @Test
  void failedRetryWaitsForTheNextRegularSync() {
    exchange.failBalanceRequests(CONNECT_TIMEOUT, 2);
    synchronizer.start();

    synchronizer.synchronizeSoon();

    await().atMost(WELL_BEFORE_THE_NEXT_SYNC).until(() -> exchange.balanceRequests() == 2);
    await().atMost(A_FEW_SYNCS).until(() -> exchange.balanceRequests() == 3);
    assertThat(gateway.accountBalances().balances()).isEqualTo(FakeExchangeTradingGateway.BALANCES);
  }

  @Test
  void regularSyncKeepsItsIntervalAfterARetry() {
    exchange.failBalanceRequests(CONNECT_TIMEOUT, 1);
    long startedAt = System.nanoTime();
    synchronizer.start();

    await().atMost(A_FEW_SYNCS).until(() -> exchange.balanceRequests() == 3);

    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
    assertThat(elapsed).isGreaterThanOrEqualTo(SYNC_INTERVAL.multipliedBy(2));
  }
}
