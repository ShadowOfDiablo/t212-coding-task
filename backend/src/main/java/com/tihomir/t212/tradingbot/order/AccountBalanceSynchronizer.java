package com.tihomir.t212.tradingbot.order;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AccountBalanceSynchronizer implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(AccountBalanceSynchronizer.class);

  private final BalanceCachingTradingGateway gateway;
  private final Duration syncInterval;
  private final Duration retryDelay;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(AccountBalanceSynchronizer::newDaemonThread);
  private boolean started;

  public AccountBalanceSynchronizer(
      BalanceCachingTradingGateway gateway, Duration syncInterval, Duration retryDelay) {
    this.gateway = gateway;
    this.syncInterval = syncInterval;
    this.retryDelay = retryDelay;
  }

  public synchronized void start() {
    if (started) {
      return;
    }
    started = true;
    long intervalMillis = syncInterval.toMillis();
    try {
      scheduler.scheduleAtFixedRate(
          this::synchronizeWithRetry, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
      log.info("Account balances are synchronized with the exchange every {}", syncInterval);
    } catch (RejectedExecutionException e) {
      log.warn("Not synchronizing account balances: the scheduler is shut down");
    }
  }

  public void synchronizeSoon() {
    try {
      scheduler.execute(this::synchronizeWithRetry);
    } catch (RejectedExecutionException e) {
      log.warn("Not synchronizing account balances: the scheduler is shut down");
    }
  }

  public void applyBalanceUpdate(Instant updateTime, List<AssetBalance> changedBalances) {
    gateway.applyBalanceUpdate(updateTime, changedBalances);
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }

  private void synchronizeWithRetry() {
    if (synchronizeBalances()) {
      return;
    }
    log.info("Retrying the account balance sync in {}", retryDelay);
    try {
      scheduler.schedule(this::retry, retryDelay.toMillis(), TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      log.info("Not retrying the account balance sync: the scheduler is shut down");
    }
  }

  private void retry() {
    if (!synchronizeBalances()) {
      log.warn("Account balance sync retry failed, the next sync runs on schedule");
    }
  }

  private boolean synchronizeBalances() {
    try {
      gateway.synchronizeBalances();
      log.debug("Account balances synchronized with the exchange");
      return true;
    } catch (RuntimeException e) {
      log.warn("Synchronizing account balances with the exchange failed", e);
      return false;
    }
  }

  private static Thread newDaemonThread(Runnable runnable) {
    Thread thread = new Thread(runnable, "account-balance-sync");
    thread.setDaemon(true);
    return thread;
  }
}
