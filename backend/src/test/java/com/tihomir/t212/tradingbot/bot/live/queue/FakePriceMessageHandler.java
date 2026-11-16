package com.tihomir.t212.tradingbot.bot.live.queue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Records what a worker asks of it. It can fail a number of times, or wait at a barrier so a test
 * can see two evaluations running at once.
 */
public final class FakePriceMessageHandler implements PriceMessageHandler {

  public static final String EVALUATED = "evaluated";
  public static final String FINISHED = "finished";

  private static final Duration BARRIER_TIMEOUT = Duration.ofSeconds(5);

  private final List<PriceMessage> evaluated = new ArrayList<>();
  private final List<String> steps = new ArrayList<>();
  private final List<RuntimeException> failures = new ArrayList<>();
  private Optional<RuntimeException> failure = Optional.empty();
  private int remainingFailures;
  private Optional<CyclicBarrier> barrier = Optional.empty();
  private int finishedCount;

  public synchronized void failEvaluations(RuntimeException newFailure, int times) {
    failure = Optional.of(newFailure);
    remainingFailures = times;
  }

  public void waitAt(CyclicBarrier newBarrier) {
    barrier = Optional.of(newBarrier);
  }

  @Override
  public void evaluatePriceMessage(PriceMessage message) {
    barrier.ifPresent(FakePriceMessageHandler::await);
    synchronized (this) {
      if (remainingFailures > 0) {
        remainingFailures--;
        throw failure.orElseThrow();
      }
      evaluated.add(message);
      steps.add(EVALUATED);
    }
  }

  @Override
  public synchronized void recordFailedEvaluation(PriceMessage message, RuntimeException failed) {
    failures.add(failed);
  }

  @Override
  public synchronized void finishPriceMessage() {
    finishedCount++;
    steps.add(FINISHED);
  }

  public synchronized List<PriceMessage> evaluated() {
    return List.copyOf(evaluated);
  }

  public synchronized List<RuntimeException> failures() {
    return List.copyOf(failures);
  }

  /** Every evaluation and finish, in the order they happened. */
  public synchronized List<String> steps() {
    return List.copyOf(steps);
  }

  public synchronized int finishedCount() {
    return finishedCount;
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await(BARRIER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (BrokenBarrierException | TimeoutException e) {
      throw new IllegalStateException("The other evaluation never ran at the same time", e);
    }
  }
}
