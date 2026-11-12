package com.tihomir.t212.tradingbot.algorithm;

import org.springframework.stereotype.Component;

/**
 * Mean reversion around one simple moving average of {@code timePeriod} candles: buys when the
 * price moves below the average and sells when it moves above it.
 */
@Component
public class SimpleMovingAverageAlgorithm extends TradingAlgorithm {

  public static final int ID = 1;
  public static final String NAME = "example-strategy-SMA";

  public SimpleMovingAverageAlgorithm() {
    super(ID, NAME, "Example: price vs. SMA", AlgorithmSettingDefinitionList.NONE);
  }

  @Override
  protected SignalGenerator newSignalGenerator(AlgorithmSettings settings) {
    return new SimpleMovingAverageSignalGenerator(settings.timePeriod());
  }
}
