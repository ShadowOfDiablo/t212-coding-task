package com.tihomir.t212.tradingbot.bot;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TuningRequestTest {

  private static final String BROKER = "binance";
  private static final String TICKER = "BTCUSDT";
  private static final Instant FROM = Instant.parse("2021-01-01T00:00:00Z");
  private static final Instant CHECK_FROM = Instant.parse("2021-01-22T00:00:00Z");
  private static final Instant TO = Instant.parse("2021-02-01T00:00:00Z");
  private static final BigDecimal STARTING_BALANCE = new BigDecimal("10000");
  private static final BigDecimal ORDER_SIZE_PERCENT = new BigDecimal("50");
  private static final BigDecimal ORDER_SIZE_PERCENT_WITH_ZEROS = new BigDecimal("50.00");
  private static final BigDecimal TIME_PERIOD = new BigDecimal("20");
  private static final int MAX_TRIALS = 50;
  private static final int OTHER_MAX_TRIALS = 60;
  private static final TunedSettingRange TIME_PERIOD_RANGE =
      new TunedSettingRange(TradingAlgorithm.TIME_PERIOD, BigDecimal.TWO, BigDecimal.TEN);
  private static final TunedSettingRange WIDER_TIME_PERIOD_RANGE =
      new TunedSettingRange(TradingAlgorithm.TIME_PERIOD, BigDecimal.TWO, TIME_PERIOD);

  @Test
  void givesTheSameSeedForTheSameSettingsInAnyOrderAndWithTrailingZeros() {
    Map<String, BigDecimal> values = new LinkedHashMap<>();
    values.put(TradingAlgorithm.ORDER_SIZE_PERCENT, ORDER_SIZE_PERCENT);
    values.put(TradingAlgorithm.TIME_PERIOD, TIME_PERIOD);
    Map<String, BigDecimal> reorderedValues = new LinkedHashMap<>();
    reorderedValues.put(TradingAlgorithm.TIME_PERIOD, TIME_PERIOD);
    reorderedValues.put(TradingAlgorithm.ORDER_SIZE_PERCENT, ORDER_SIZE_PERCENT_WITH_ZEROS);

    long seed = request(values, TIME_PERIOD_RANGE, MAX_TRIALS).getSeed();

    assertThat(request(reorderedValues, TIME_PERIOD_RANGE, MAX_TRIALS).getSeed()).isEqualTo(seed);
  }

  @Test
  void givesAnotherSeedWhenTheRangeOrTheTryCountChanges() {
    Map<String, BigDecimal> values = Map.of(TradingAlgorithm.TIME_PERIOD, TIME_PERIOD);

    long seed = request(values, TIME_PERIOD_RANGE, MAX_TRIALS).getSeed();

    assertThat(request(values, WIDER_TIME_PERIOD_RANGE, MAX_TRIALS).getSeed()).isNotEqualTo(seed);
    assertThat(request(values, TIME_PERIOD_RANGE, OTHER_MAX_TRIALS).getSeed()).isNotEqualTo(seed);
  }

  private static TuningRequest request(
      Map<String, BigDecimal> values, TunedSettingRange range, int maxTrials) {
    AlgorithmSettingsRequest settings =
        new AlgorithmSettingsRequest(
            AlgorithmType.SIMPLE_MOVING_AVERAGE, CandleInterval.FIFTEEN_MINUTES, true, values);
    return new TuningRequest(
        new TrainingRunRequest(BROKER, TICKER, FROM, TO, settings, STARTING_BALANCE),
        CHECK_FROM,
        List.of(range),
        List.of(),
        maxTrials);
  }
}
