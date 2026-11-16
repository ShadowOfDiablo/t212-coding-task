package com.tihomir.t212.tradingbot.bot.tuning;

import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.exception.InvalidAlgorithmSettingsException;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingDefinition;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.training.TrainingRunRequest;
import com.tihomir.t212.tradingbot.bot.training.TrainingService;
import com.tihomir.t212.tradingbot.bot.training.TrainingSetup;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.order.model.TickerTradingRules;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One tuning: turns the requested ranges into dimensions, scores tries on the tune range, and
 * builds the setup that records the best try over the whole range. Candles are read once per
 * timeframe.
 */
final class TuningSession {

  static final String TIMEFRAME_DIMENSION = "timeframe";

  /** An optional setting's on/off dimension is its name plus this, e.g. stopLossPercentEnabled. */
  static final String ENABLED_SUFFIX = "Enabled";

  static final double SETTING_OFF = 0;

  private static final Logger log = LoggerFactory.getLogger(TuningSession.class);

  private static final int NUMBER_SCALE = 2;
  private static final double SETTING_ON = 1;

  private final TuningRequest request;
  private final TradingAlgorithm algorithm;
  private final TickerTradingRules tradingRules;
  private final TrainingService trainingService;
  private final List<CandleInterval> timeframes;
  private final Map<String, AlgorithmSettingDefinition> definitions;
  private final List<String> optionalTunedNames;
  private final List<TuningDimension> dimensions;
  private final Map<CandleInterval, CandleList> tuneCandlesByTimeframe =
      new EnumMap<>(CandleInterval.class);
  private final Map<CandleInterval, RuntimeException> candleFailuresByTimeframe =
      new EnumMap<>(CandleInterval.class);

  /**
   * @throws InvalidAlgorithmSettingsException if a range can't be tuned, or nothing can
   */
  TuningSession(
      TuningRequest request,
      TradingAlgorithm algorithm,
      TickerTradingRules tradingRules,
      TrainingService trainingService) {
    this.request = request;
    this.algorithm = algorithm;
    this.tradingRules = tradingRules;
    this.trainingService = trainingService;
    this.timeframes = request.timeframes().stream().distinct().toList();
    this.definitions =
        algorithm.settingDefinitions().definitions().stream()
            .collect(Collectors.toMap(AlgorithmSettingDefinition::name, Function.identity()));
    this.dimensions = checkedDimensions();
    this.optionalTunedNames =
        request.settingRanges().stream()
            .map(TunedSettingRange::name)
            .filter(name -> !definitions.get(name).required())
            .toList();
  }

  List<TuningDimension> dimensions() {
    return dimensions;
  }

  /** Replays one try on the tune range. A try that fails is logged and scored as the worst. */
  TuningTrial scoredTrial(TuningPoint drawnPoint) {
    TuningPoint point = usedPoint(drawnPoint);
    AlgorithmSettingsRequest settings = settingsRequest(point);
    try {
      AlgorithmSettings resolved = algorithm.resolveSettings(settings);
      TrainingSetup setup =
          new TrainingSetup(tuneRequest(settings), algorithm, resolved, tradingRules);
      BigDecimal finalValue =
          trainingService.replayFinalValue(setup, tuneCandles(resolved.timeframe()));
      return new TuningTrial(point, settings, returnFraction(finalValue));
    } catch (RuntimeException e) {
      log.warn(
          "Tuning {} {}: the try with {} {} failed: {}",
          request.training().broker(),
          request.training().ticker(),
          settings.timeframe().code(),
          settings.values(),
          e.getMessage());
      return new TuningTrial(point, settings, TuningTrial.FAILED_TRIAL_SCORE);
    }
  }

  /**
   * These settings (the best try's, or the base ones) set up for the whole range, tune and check
   * range together, so the recorded run shows the entire period.
   */
  TrainingSetup fullRangeSetup(AlgorithmSettingsRequest settings) {
    TrainingRunRequest training = request.training();
    TrainingRunRequest fullRange =
        new TrainingRunRequest(
            training.broker(),
            training.ticker(),
            training.from(),
            training.to(),
            settings,
            training.startingBalance());
    return new TrainingSetup(
        fullRange, algorithm, algorithm.resolveSettings(settings), tradingRules);
  }

  /** An optional setting the try switched off isn't part of the try. */
  private TuningPoint usedPoint(TuningPoint drawnPoint) {
    Set<String> switchedOff =
        optionalTunedNames.stream()
            .filter(name -> isSwitchedOff(drawnPoint, name))
            .collect(Collectors.toSet());
    return drawnPoint.without(switchedOff);
  }

  private static boolean isSwitchedOff(TuningPoint point, String settingName) {
    return point
        .value(settingName + ENABLED_SUFFIX)
        .filter(value -> value == SETTING_OFF)
        .isPresent();
  }

  private AlgorithmSettingsRequest settingsRequest(TuningPoint point) {
    AlgorithmSettingsRequest base = request.training().settings();
    Map<String, BigDecimal> values = new HashMap<>(base.values());
    dimensions.stream()
        .filter(dimension -> dimension.kind() != TuningDimensionKind.CATEGORY)
        .forEach(dimension -> putTunedValue(values, dimension, point));
    CandleInterval timeframe =
        point
            .value(TIMEFRAME_DIMENSION)
            .map(index -> timeframes.get((int) Math.round(index)))
            .orElse(base.timeframe());
    return new AlgorithmSettingsRequest(base.algorithmType(), timeframe, base.liveOnTick(), values);
  }

  /** A tuned setting the try switched off is left out entirely. */
  private static void putTunedValue(
      Map<String, BigDecimal> values, TuningDimension dimension, TuningPoint point) {
    values.remove(dimension.name());
    point
        .value(dimension.name())
        .ifPresent(value -> values.put(dimension.name(), settingValue(dimension, value)));
  }

  private static BigDecimal settingValue(TuningDimension dimension, double value) {
    if (dimension.kind() == TuningDimensionKind.WHOLE_NUMBER) {
      return BigDecimal.valueOf(Math.round(value));
    }
    return BigDecimal.valueOf(value).setScale(NUMBER_SCALE, RoundingMode.HALF_EVEN);
  }

  /** Read once per timeframe. A timeframe whose candles can't be read fails its later tries too. */
  private CandleList tuneCandles(CandleInterval timeframe) {
    RuntimeException earlierFailure = candleFailuresByTimeframe.get(timeframe);
    if (earlierFailure != null) {
      throw earlierFailure;
    }
    CandleList stored = tuneCandlesByTimeframe.get(timeframe);
    if (stored != null) {
      return stored;
    }
    try {
      CandleList candles =
          trainingService.closedCandles(tuneRequest(request.training().settings()), timeframe);
      tuneCandlesByTimeframe.put(timeframe, candles);
      return candles;
    } catch (RuntimeException e) {
      candleFailuresByTimeframe.put(timeframe, e);
      throw e;
    }
  }

  private TrainingRunRequest tuneRequest(AlgorithmSettingsRequest settings) {
    TrainingRunRequest training = request.training();
    return new TrainingRunRequest(
        training.broker(),
        training.ticker(),
        training.from(),
        request.checkFrom(),
        settings,
        training.startingBalance());
  }

  /** (final value - starting balance) / starting balance. */
  private double returnFraction(BigDecimal finalValue) {
    BigDecimal startingBalance = request.training().startingBalance();
    return finalValue
        .subtract(startingBalance)
        .divide(startingBalance, MathContext.DECIMAL64)
        .doubleValue();
  }

  /** Each tuned setting, an on/off for each optional one, and the timeframe when it is tuned. */
  private List<TuningDimension> checkedDimensions() {
    List<TuningDimension> dimensions =
        new ArrayList<>(
            request.settingRanges().stream()
                .map(range -> settingDimension(range, definitions))
                .toList());
    Set<String> names = dimensions.stream().map(TuningDimension::name).collect(Collectors.toSet());
    if (names.size() != dimensions.size()) {
      throw new InvalidAlgorithmSettingsException("A setting is tuned twice");
    }
    names.stream()
        .filter(name -> !definitions.get(name).required())
        .sorted()
        .map(TuningSession::onOffDimension)
        .forEach(dimensions::add);
    if (!timeframes.isEmpty()) {
      dimensions.add(
          new TuningDimension(
              TIMEFRAME_DIMENSION, TuningDimensionKind.CATEGORY, 0, timeframes.size() - 1));
    }
    if (dimensions.isEmpty()) {
      throw new InvalidAlgorithmSettingsException(
          "Nothing to tune: give at least one setting range or timeframe");
    }
    return List.copyOf(dimensions);
  }

  private static TuningDimension onOffDimension(String settingName) {
    return new TuningDimension(
        settingName + ENABLED_SUFFIX, TuningDimensionKind.CATEGORY, SETTING_OFF, SETTING_ON);
  }

  private static TuningDimension settingDimension(
      TunedSettingRange range, Map<String, AlgorithmSettingDefinition> definitions) {
    AlgorithmSettingDefinition definition = definitions.get(range.name());
    if (definition == null || TradingAlgorithm.UPDATE_TIME_SECONDS.equals(range.name())) {
      throw new InvalidAlgorithmSettingsException("Setting can't be tuned: " + range.name());
    }
    boolean insideDefinition =
        range.minimum().compareTo(definition.minimum()) >= 0
            && range.maximum().compareTo(definition.maximum()) <= 0
            && range.minimum().compareTo(range.maximum()) <= 0;
    if (!insideDefinition) {
      throw new InvalidAlgorithmSettingsException(
          range.name()
              + " is tuned between "
              + definition.minimum().toPlainString()
              + " and "
              + definition.maximum().toPlainString()
              + ", with the minimum at most the maximum");
    }
    if (!definition.wholeNumber()) {
      return new TuningDimension(
          range.name(),
          TuningDimensionKind.NUMBER,
          range.minimum().doubleValue(),
          range.maximum().doubleValue());
    }
    if (!isWholeNumber(range.minimum()) || !isWholeNumber(range.maximum())) {
      throw new InvalidAlgorithmSettingsException(range.name() + " is tuned in whole numbers");
    }
    return new TuningDimension(
        range.name(),
        TuningDimensionKind.WHOLE_NUMBER,
        range.minimum().doubleValue(),
        range.maximum().doubleValue());
  }

  private static boolean isWholeNumber(BigDecimal value) {
    return value.stripTrailingZeros().scale() <= 0;
  }
}
