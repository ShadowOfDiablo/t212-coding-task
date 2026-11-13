package com.tihomir.t212.tradingbot.algorithm.indicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** The average of the last {@code period} values, updated one value at a time. */
public final class SimpleMovingAverage {

  private final int period;
  private final BigDecimal periodAsDecimal;
  private final Deque<BigDecimal> window = new ArrayDeque<>();
  private BigDecimal windowSum = BigDecimal.ZERO;

  public SimpleMovingAverage(int period) {
    if (period < 1) {
      throw new IllegalArgumentException("period must be at least 1");
    }
    this.period = period;
    this.periodAsDecimal = BigDecimal.valueOf(period);
  }

  public void addValue(BigDecimal value) {
    window.addLast(value);
    windowSum = windowSum.add(value);
    if (window.size() > period) {
      windowSum = windowSum.subtract(window.removeFirst());
    }
  }

  /** Empty until {@code period} values have been added. */
  public Optional<BigDecimal> currentValue() {
    if (window.size() < period) {
      return Optional.empty();
    }
    return Optional.of(windowSum.divide(periodAsDecimal, MathContext.DECIMAL64));
  }
}
