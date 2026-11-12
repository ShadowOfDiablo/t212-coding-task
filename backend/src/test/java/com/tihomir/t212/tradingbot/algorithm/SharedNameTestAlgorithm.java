package com.tihomir.t212.tradingbot.algorithm;

import java.util.List;

/** Wrongly names one of its own settings like a shared one. */
final class SharedNameTestAlgorithm extends TradingAlgorithm {

  SharedNameTestAlgorithm() {
    super(
        "test-shared-name",
        "Shared name test",
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
