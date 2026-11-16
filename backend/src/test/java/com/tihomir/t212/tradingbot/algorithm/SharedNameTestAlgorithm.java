package com.tihomir.t212.tradingbot.algorithm;

import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingDefinition;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingDefinitionList;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import java.util.List;

/** Wrongly names one of its own settings like a shared one. */
final class SharedNameTestAlgorithm extends TradingAlgorithm {

  public SharedNameTestAlgorithm() {
    super(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        "test-shared-name",
        "Shared name test",
        "A test algorithm.",
        new AlgorithmSettingDefinitionList(
            List.of(
                AlgorithmSettingDefinition.optionalWholeNumber(
                    TradingAlgorithm.TIME_PERIOD, "Time period", 1, 2))));
  }

  @Override
  protected SignalGenerator newSignalGenerator(AlgorithmSettings settings) {
    return candle -> TradingSignal.HOLD;
  }
}
