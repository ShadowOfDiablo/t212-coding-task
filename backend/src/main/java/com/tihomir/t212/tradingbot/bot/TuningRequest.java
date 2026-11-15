package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A tuning to run, built from a request body already checked with {@code @Valid}. The tries are
 * scored on {@code [training.from, checkFrom)}; the best one is recorded on {@code [checkFrom,
 * training.to)}.
 *
 * @param training the range, the base settings and the starting balance; settings that aren't tuned
 *     keep their base value
 * @param timeframes the timeframes to choose from; empty keeps the base timeframe
 * @param randomSeed makes the tries repeatable
 */
public record TuningRequest(
    TrainingRunRequest training,
    Instant checkFrom,
    List<TunedSettingRange> settingRanges,
    List<CandleInterval> timeframes,
    int maxTrials,
    Optional<Long> randomSeed) {

  public TuningRequest {
    settingRanges = List.copyOf(settingRanges);
    timeframes = List.copyOf(timeframes);
  }
}
