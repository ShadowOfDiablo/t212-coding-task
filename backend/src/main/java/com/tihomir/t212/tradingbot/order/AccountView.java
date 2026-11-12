package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.util.Optional;

/** What the order checks need to know about the account a run trades on. */
public interface AccountView {

  String ticker();

  /** Free quote currency (USDT). */
  BigDecimal cash();

  /** Free quantity of the ticker's base asset. */
  BigDecimal baseQuantity();

  /** The average buy price of the position held; empty while nothing is held. */
  Optional<BigDecimal> entryPrice();
}
