package com.tihomir.t212.tradingbot.bot.live.queue;

/** What evaluates the prices of one ticker; a worker calls it for each message of that ticker. */
public interface PriceMessageHandler {

  /** Evaluates the price. A failure is thrown, so the worker can retry the message. */
  void evaluatePriceMessage(PriceMessage message);

  /** The message failed for good: it won't be retried. */
  void recordFailedEvaluation(PriceMessage message, RuntimeException failure);

  /** The message is done, evaluated or dropped; called once per message, after any retries. */
  void finishPriceMessage();
}
