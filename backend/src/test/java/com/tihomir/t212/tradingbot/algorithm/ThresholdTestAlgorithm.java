package com.tihomir.t212.tradingbot.algorithm;

import com.tihomir.t212.tradingbot.algorithm.exception.InvalidAlgorithmSettingsException;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingDefinition;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingDefinitionList;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import java.math.BigDecimal;
import java.util.List;

/** An algorithm with its own settings: a required window and an optional threshold. */
final class ThresholdTestAlgorithm extends TradingAlgorithm {

  static final String WINDOW = "window";
  static final String THRESHOLD = "threshold";
  static final int MIN_WINDOW_WITH_THRESHOLD = 3;

  public ThresholdTestAlgorithm() {
    super(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        "test-threshold",
        "Threshold test",
        "A test algorithm.",
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
