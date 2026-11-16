package com.tihomir.t212.tradingbot.bot.live.queue;

import com.tihomir.t212.tradingbot.marketdata.history.CandleDownloadFailedException;
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.exception.ExchangeUnavailableException;
import org.springframework.dao.DataAccessException;

/** Which failed evaluations are tried again, and how often. */
public final class EvaluationRetryPolicy {

  /** A message is evaluated at most this many times, the first try included. */
  public static final int MAX_ATTEMPTS = 3;

  private EvaluationRetryPolicy() {}

  /**
   * Retried: the exchange rejected the order and executed nothing, or the failure came before any
   * order was sent (balances, candles, database). Never an order whose status is unknown: it may
   * have been executed, and a retry could trade twice.
   */
  public static boolean isRetryable(RuntimeException failure) {
    return failure instanceof ExchangeRejectedRequestException
        || failure instanceof ExchangeUnavailableException
        || failure instanceof CandleDownloadFailedException
        || failure instanceof DataAccessException;
  }

  public static boolean hasAttemptsLeft(PriceMessage message) {
    return message.attempt() < MAX_ATTEMPTS;
  }
}
