package com.tihomir.t212.tradingbot.algorithm;

import java.math.BigDecimal;
import java.util.List;

/** An algorithm with its own settings: a required window and an optional threshold. */
final class ThresholdTestAlgorithm extends TradingAlgorithm {

  static final String ID = "test-threshold";
  static final String WINDOW = "window";
  static final String THRESHOLD = "threshold";
  static final int MIN_WINDOW_WITH_THRESHOLD = 3;

  ThresholdTestAlgorithm() {
    super(
        ID,
        "Threshold test",
        new AlgorithmSettingDefinitionList(
            List.of(
                AlgorithmSettingDefinition.requiredWholeNumber(WINDOW, "Window", 5, 1, 10),
                AlgorithmSettingDefinition.optionalNumber(THRESHOLD, "Threshold", "0", "1"))));
  }

  @Override
  protected void checkOptionRules(AlgorithmSettings settings) {
    settings
        .options()
        .optionalValue(THRESHOLD)
        .filter(threshold -> settings.options().wholeNumber(WINDOW) < MIN_WINDOW_WITH_THRESHOLD)
        .ifPresent(ThresholdTestAlgorithm::refuseShortWindow);
  }

  @Override
  protected SignalGenerator newSignalGenerator(AlgorithmSettings settings) {
    return candle -> TradingSignal.HOLD;
  }

  private static void refuseShortWindow(BigDecimal threshold) {
    throw new InvalidAlgorithmSettingsException("A threshold needs a window of at least 3");
  }
}
