package com.tihomir.t212.tradingbot.bot.tuning;

import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.training.TrainingRunRequest;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A tuning to run, built from a request body already checked with {@code @Valid}. The tries are
 * scored on {@code [training.from, checkFrom)}; the best one is recorded on the whole {@code
 * [training.from, training.to)}, so its return from {@code checkFrom} on shows unseen data.
 *
 * @param training the range, the base settings and the starting balance; settings that aren't tuned
 *     keep their base value
 * @param timeframes the timeframes to choose from; empty keeps the base timeframe
 */
public record TuningRequest(
    TrainingRunRequest training,
    Instant checkFrom,
    List<TunedSettingRange> settingRanges,
    List<CandleInterval> timeframes,
    int maxTrials) {

  private static final String FIELD_SEPARATOR = "|";
  private static final String NOT_SET = "-";

  public TuningRequest {
    settingRanges = List.copyOf(settingRanges);
    timeframes = List.copyOf(timeframes);
  }

  /**
   * The seed of the tries, derived from the request alone, so the same tuning always tries the same
   * settings and finds the same best. Equal numbers count as equal ({@code 10} and {@code 10.00}),
   * and the base settings count in name order. The ranges and timeframes keep their order, which
   * decides how the tries are drawn.
   */
  public long getSeed() {
    AlgorithmSettingsRequest settings = training.settings();
    List<String> fields =
        List.of(
            training.broker(),
            training.ticker(),
            training.from().toString(),
            checkFrom.toString(),
            training.to().toString(),
            settings.algorithmType().name(),
            settings.timeframe().code(),
            String.valueOf(settings.liveOnTick()),
            sortedValuesText(settings.values()),
            settingRanges.stream().map(TuningRequest::rangeText).collect(Collectors.joining(",")),
            timeframes.stream().map(CandleInterval::code).collect(Collectors.joining(",")),
            String.valueOf(maxTrials),
            plainText(training.startingBalance()));
    return String.join(FIELD_SEPARATOR, fields).hashCode();
  }

  private static String sortedValuesText(Map<String, BigDecimal> values) {
    Map<String, String> sorted = new TreeMap<>();
    values.forEach((name, value) -> sorted.put(name, plainText(value)));
    return sorted.toString();
  }

  private static String rangeText(TunedSettingRange range) {
    return range.name() + "=" + plainText(range.minimum()) + ".." + plainText(range.maximum());
  }

  private static String plainText(BigDecimal value) {
    if (value == null) {
      return NOT_SET;
    }
    return AlgorithmSettingValues.plainValue(value).toPlainString();
  }
}
