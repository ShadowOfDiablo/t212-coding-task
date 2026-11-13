package com.tihomir.t212.tradingbot.algorithm;

import java.util.List;

/**
 * A trading algorithm: it turns prices into signals and nothing else. Whether a signal becomes an
 * order is decided by the order checks.
 *
 * <p>Every algorithm shares the settings below. An algorithm adds its own optional settings through
 * {@code optionDefinitions}; they are stored with the shared ones.
 */
public abstract class TradingAlgorithm {

  public static final String TIME_PERIOD = "timePeriod";
  public static final String UPDATE_TIME_SECONDS = "updateTimeSeconds";
  public static final String ORDER_SIZE_PERCENT = "orderSizePercent";
  public static final String RISK_PER_TRADE_PERCENT = "riskPerTradePercent";
  public static final String STOP_LOSS_PERCENT = "stopLossPercent";

  private static final int DEFAULT_TIME_PERIOD = 20;
  private static final int MIN_TIME_PERIOD = 1;
  private static final int MAX_TIME_PERIOD = 1000;
  private static final int MIN_UPDATE_TIME_SECONDS = 1;
  private static final int MAX_UPDATE_TIME_SECONDS = 86_400;

  static final AlgorithmSettingDefinitionList SHARED_DEFINITIONS =
      new AlgorithmSettingDefinitionList(
          List.of(
              AlgorithmSettingDefinition.requiredWholeNumber(
                  TIME_PERIOD,
                  "Time period (candles)",
                  DEFAULT_TIME_PERIOD,
                  MIN_TIME_PERIOD,
                  MAX_TIME_PERIOD),
              AlgorithmSettingDefinition.optionalWholeNumber(
                  UPDATE_TIME_SECONDS,
                  "Update every (seconds)",
                  MIN_UPDATE_TIME_SECONDS,
                  MAX_UPDATE_TIME_SECONDS),
              AlgorithmSettingDefinition.requiredNumber(
                  ORDER_SIZE_PERCENT, "Each buy spends (% of cash)", "100", "0.01", "100"),
              AlgorithmSettingDefinition.optionalNumber(
                  RISK_PER_TRADE_PERCENT, "Risk per trade (% of portfolio)", "0.01", "100"),
              AlgorithmSettingDefinition.optionalNumber(
                  STOP_LOSS_PERCENT, "Stop loss (% below the buy)", "0.01", "99")));

  private final AlgorithmType type;
  private final String name;
  private final String displayName;
  private final AlgorithmSettingDefinitionList optionDefinitions;

  /**
   * @param name the algorithm's text name, e.g. {@code example-strategy-SMA}
   */
  protected TradingAlgorithm(
      AlgorithmType type,
      String name,
      String displayName,
      AlgorithmSettingDefinitionList optionDefinitions) {
    this.type = type;
    this.name = name;
    this.displayName = displayName;
    this.optionDefinitions = optionDefinitions;
  }

  public final AlgorithmType type() {
    return type;
  }

  public final String name() {
    return name;
  }

  public final String displayName() {
    return displayName;
  }

  public final AlgorithmSettingDefinitionList optionDefinitions() {
    return optionDefinitions;
  }

  /** The shared settings followed by this algorithm's own. */
  public final AlgorithmSettingDefinitionList settingDefinitions() {
    return SHARED_DEFINITIONS.followedBy(optionDefinitions);
  }

  /**
   * Checks the requested settings. Required settings without a value take their default; optional
   * settings without a value stay empty and skip every check.
   *
   * @throws InvalidAlgorithmSettingsException if a value or a combination isn't allowed
   */
  public final AlgorithmSettings resolveSettings(AlgorithmSettingsRequest request) {
    if (type != request.algorithmType()) {
      throw new InvalidAlgorithmSettingsException(
          "Settings for algorithm " + request.algorithmType() + " can't be used with " + type);
    }
    AlgorithmSettingValues values = settingDefinitions().resolveValues(request.values());
    AlgorithmSettings settings =
        new AlgorithmSettings(
            type,
            values.wholeNumber(TIME_PERIOD),
            request.timeframe(),
            request.liveOnTick(),
            values.optionalWholeNumber(UPDATE_TIME_SECONDS),
            values.value(ORDER_SIZE_PERCENT),
            values.optionalValue(RISK_PER_TRADE_PERCENT),
            values.optionalValue(STOP_LOSS_PERCENT),
            values.only(optionDefinitions.names()));
    checkSharedRules(settings);
    checkOptionRules(settings);
    return settings;
  }

  /** A fresh, stateful signal generator for one run. */
  public final SignalGenerator createSignalGenerator(AlgorithmSettings settings) {
    return newSignalGenerator(settings);
  }

  /** Closed candles a generator needs before it can give its first signal. */
  public int warmUpCandles(AlgorithmSettings settings) {
    return settings.timePeriod();
  }

  /** Rules between this algorithm's own settings. Empty optional settings must be skipped. */
  protected void checkOptionRules(AlgorithmSettings settings) {}

  protected abstract SignalGenerator newSignalGenerator(AlgorithmSettings settings);

  private static void checkSharedRules(AlgorithmSettings settings) {
    if (settings.liveOnTick() && settings.updateTimeSeconds().isPresent()) {
      throw new InvalidAlgorithmSettingsException(
          "updateTimeSeconds must be empty when liveOnTick is on");
    }
    if (!settings.liveOnTick() && settings.updateTimeSeconds().isEmpty()) {
      throw new InvalidAlgorithmSettingsException(
          "updateTimeSeconds is required when liveOnTick is off");
    }
    if (settings.riskPerTradePercent().isPresent() != settings.stopLossPercent().isPresent()) {
      throw new InvalidAlgorithmSettingsException(
          "riskPerTradePercent and stopLossPercent are set together");
    }
  }
}
