package com.tihomir.t212.tradingbot.bot;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Runs each task at once on the calling thread, or refuses it once shut down. */
public final class FakeExecutorService extends AbstractExecutorService {

  private volatile boolean shutDown;

  @Override
  public void execute(Runnable task) {
    if (shutDown) {
      throw new RejectedExecutionException("The executor is shut down");
    }
    task.run();
  }

  @Override
  public void shutdown() {
    shutDown = true;
  }

  @Override
  public List<Runnable> shutdownNow() {
    shutDown = true;
    return List.of();
  }

  @Override
  public boolean isShutdown() {
    return shutDown;
  }

  @Override
  public boolean isTerminated() {
    return shutDown;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) {
    return shutDown;
  }
}
