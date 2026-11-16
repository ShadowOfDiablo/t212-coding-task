package com.tihomir.t212.tradingbot.algorithm.indicator;

import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Signals only when the price crosses to the other side of the average, so it doesn't buy again on
 * every candle below it. Only closed candles go into the average; a forming candle's price is
 * compared with the average of the closed ones.
 */
final class SimpleMovingAverageSignalGenerator implements SignalGenerator {

  private static final int NO_SIDE_YET = 0;

  private final SimpleMovingAverage average;
  private int previousSide = NO_SIDE_YET;

  SimpleMovingAverageSignalGenerator(int timePeriod) {
    this.average = new SimpleMovingAverage(timePeriod);
  }

  @Override
  public TradingSignal onPrice(CandleUpdate candle) {
    if (candle.closed()) {
      average.addValue(candle.close());
    }
    Optional<BigDecimal> averageValue = average.currentValue();
    if (averageValue.isEmpty()) {
      return TradingSignal.HOLD;
    }
    int side = Integer.signum(candle.close().compareTo(averageValue.get()));
    if (side == NO_SIDE_YET) {
      return TradingSignal.HOLD;
    }
    TradingSignal signal = crossingSignal(side);
    previousSide = side;
    return signal;
  }

  private TradingSignal crossingSignal(int side) {
    if (previousSide == NO_SIDE_YET || side == previousSide) {
      return TradingSignal.HOLD;
    }
    if (side < 0) {
      return TradingSignal.BUY;
    }
    return TradingSignal.SELL;
  }
}
