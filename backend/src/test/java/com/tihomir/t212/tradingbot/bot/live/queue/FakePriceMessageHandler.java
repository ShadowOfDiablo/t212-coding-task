package com.tihomir.t212.tradingbot.bot.live.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Records what a worker asks of it. It can fail a number of times. */
public final class FakePriceMessageHandler implements PriceMessageHandler {

  private final List<PriceMessage> evaluated = new ArrayList<>();
  private final List<RuntimeException> failures = new ArrayList<>();
  private Optional<RuntimeException> failure = Optional.empty();
  private int remainingFailures;
  private int finishedCount;

  public synchronized void failEvaluations(RuntimeException newFailure, int times) {
    failure = Optional.of(newFailure);
    remainingFailures = times;
  }

  @Override
  public synchronized void evaluatePriceMessage(PriceMessage message) {
    if (remainingFailures > 0) {
      remainingFailures--;
      throw failure.orElseThrow();
    }
    evaluated.add(message);
  }

  @Override
  public synchronized void recordFailedEvaluation(PriceMessage message, RuntimeException failed) {
    failures.add(failed);
  }

  @Override
  public synchronized void finishPriceMessage() {
    finishedCount++;
  }

  public synchronized List<PriceMessage> evaluated() {
    return List.copyOf(evaluated);
  }

  public synchronized List<RuntimeException> failures() {
    return List.copyOf(failures);
  }

  public synchronized int finishedCount() {
    return finishedCount;
  }
}
