package com.tihomir.t212.tradingbot.bot.live.queue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One broker's live prices waiting to be evaluated, oldest first, and the handler of each ticker.
 * Producers never block: a full queue drops the price with a warning. Retries go to the front.
 */
public final class LiveEvaluationQueue {

  static final int CAPACITY = 1_000;

  private static final Logger log = LoggerFactory.getLogger(LiveEvaluationQueue.class);

  private final String broker;
  private final LinkedBlockingDeque<PriceMessage> messages = new LinkedBlockingDeque<>(CAPACITY);
  private final Map<String, PriceMessageHandler> handlers = new ConcurrentHashMap<>();

  public LiveEvaluationQueue(String broker) {
    this.broker = broker;
  }

  public void registerHandler(String ticker, PriceMessageHandler handler) {
    handlers.put(ticker, handler);
  }

  public void unregisterHandler(String ticker) {
    handlers.remove(ticker);
  }

  /**
   * @return false when the queue is full and the price was dropped
   */
  public boolean offerLast(PriceMessage message) {
    if (messages.offerLast(message)) {
      return true;
    }
    log.warn(
        "{} {}: evaluation queue is full, dropped the price {}",
        broker,
        message.ticker(),
        message.price());
    return false;
  }

  /**
   * @return false when the queue is full and the retry was dropped
   */
  boolean offerFirst(PriceMessage message) {
    return messages.offerFirst(message);
  }

  PriceMessage takeFirst() throws InterruptedException {
    return messages.takeFirst();
  }

  Optional<PriceMessageHandler> handlerOf(String ticker) {
    return Optional.ofNullable(handlers.get(ticker));
  }

  String broker() {
    return broker;
  }
}
