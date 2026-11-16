package com.tihomir.t212.tradingbot.bot.live.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker threads that take prices from one broker's queue and hand each to its ticker's handler.
 * Different tickers are evaluated in parallel; a worker holds the ticker's handler lock for the
 * whole message, so one ticker never runs twice at once. A worker never stops on a failure.
 */
public final class EvaluationWorkerPool implements AutoCloseable {

  static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

  private static final Logger log = LoggerFactory.getLogger(EvaluationWorkerPool.class);

  private final LiveEvaluationQueue queue;
  private final List<Thread> workers;

  public EvaluationWorkerPool(LiveEvaluationQueue queue, int workerCount) {
    this.queue = queue;
    this.workers =
        IntStream.range(0, workerCount)
            .mapToObj(index -> startWorker(queue.broker() + "-evaluation-" + index))
            .toList();
  }

  /**
   * Evaluates one message as a worker does. A retryable failure goes back to the front of the queue
   * while attempts are left; any other outcome finishes the message. The ticker's next price waits
   * until this one is finished or back in the queue.
   */
  public void handleMessage(PriceMessage message) {
    Optional<PriceMessageHandler> handler = queue.handlerOf(message.ticker());
    if (handler.isEmpty()) {
      log.debug("{} {}: no running bot, dropped a price", queue.broker(), message.ticker());
      return;
    }
    synchronized (handler.get()) {
      try {
        handler.get().evaluatePriceMessage(message);
        handler.get().finishPriceMessage();
      } catch (RuntimeException e) {
        handleFailure(handler.get(), message, e);
      }
    }
  }

  @Override
  public void close() {
    workers.forEach(Thread::interrupt);
    workers.forEach(EvaluationWorkerPool::awaitWorker);
  }

  private void handleFailure(
      PriceMessageHandler handler, PriceMessage message, RuntimeException failure) {
    if (EvaluationRetryPolicy.isRetryable(failure)
        && EvaluationRetryPolicy.hasAttemptsLeft(message)
        && queue.offerFirst(message.withNextAttempt())) {
      log.warn(
          "{} {}: evaluation try {} failed, retrying: {}",
          queue.broker(),
          message.ticker(),
          message.attempt(),
          failure.toString());
      return;
    }
    try {
      handler.recordFailedEvaluation(message, failure);
    } catch (RuntimeException e) {
      log.error("{} {}: recording a failed evaluation failed", queue.broker(), message.ticker(), e);
    } finally {
      handler.finishPriceMessage();
    }
  }

  private Thread startWorker(String name) {
    return Thread.ofPlatform().daemon().name(name).start(this::workLoop);
  }

  private void workLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        handleMessage(queue.takeFirst());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (RuntimeException e) {
        log.error("{}: an evaluation worker caught a failure and goes on", queue.broker(), e);
      }
    }
  }

  private static void awaitWorker(Thread worker) {
    try {
      if (!worker.join(SHUTDOWN_TIMEOUT)) {
        log.warn("{} did not stop in time", worker.getName());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
