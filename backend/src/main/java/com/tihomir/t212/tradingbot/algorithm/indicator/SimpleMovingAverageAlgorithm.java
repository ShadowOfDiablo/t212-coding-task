package com.tihomir.t212.tradingbot.algorithm.indicator;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingDefinitionList;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import org.springframework.stereotype.Component;

/**
 * Mean reversion around one simple moving average of {@code timePeriod} candles: buys when the
 * price moves below the average and sells when it moves above it.
 */
@Component
public class SimpleMovingAverageAlgorithm extends TradingAlgorithm {

  public static final String NAME = "example-strategy-SMA";

  public SimpleMovingAverageAlgorithm() {
    super(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        NAME,
        "SMA",
        "Mean reversion around a simple moving average of the last time period candles: buys when"
            + " the price closes below the average, sells when it closes back above it.",
        AlgorithmSettingDefinitionList.NONE);
  }

  @Override
  protected SignalGenerator newSignalGenerator(AlgorithmSettings settings) {
    return new SimpleMovingAverageSignalGenerator(settings.timePeriod());
  }
}
