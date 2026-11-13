package com.tihomir.t212.tradingbot.bot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * How far a training run has come.
 *
 * @param simulatedTime the close time of the last replayed candle; empty until the first one
 * @param candleCount zero until the candles have been read
 */
public record TrainingProgress(
    Optional<Instant> simulatedTime,
    int replayedCandleCount,
    int candleCount,
    BigDecimal cash,
    BigDecimal value,
    RunCounts counts) {

  static TrainingProgress beforeFirstCandle(BigDecimal startingBalance) {
    return new TrainingProgress(
        Optional.empty(), 0, 0, startingBalance, startingBalance, RunCounts.NONE);
  }
}
