package com.tihomir.t212.tradingbot.order;

/** Why the order checks refused a signal. */
public enum RejectionReason {
  INSUFFICIENT_FUNDS,
  NO_POSITION,
  BELOW_MIN_ORDER_VALUE
}
