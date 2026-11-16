package com.tihomir.t212.tradingbot.order.model;

import java.math.BigDecimal;

/**
 * @param lotStepSize Binance's LOT_SIZE step: the smallest amount a coin quantity can change by.
 *     For BTCUSDT it is 0.00001 BTC, so 0.123456 BTC is sent as 0.12345 BTC.
 * @param minOrderValue the smallest order value in the quote asset; zero when there is none
 */
public record TickerTradingRules(BigDecimal lotStepSize, BigDecimal minOrderValue) {

  public TickerTradingRules {
    if (lotStepSize.signum() <= 0) {
      throw new IllegalArgumentException("The lot step must be greater than zero");
    }
  }

  /** The quantity rounded down to a whole number of lot steps; zero when it is less than one. */
  public BigDecimal roundDownToLotStep(BigDecimal quantity) {
    return quantity.divideToIntegralValue(lotStepSize).multiply(lotStepSize).stripTrailingZeros();
  }
}
