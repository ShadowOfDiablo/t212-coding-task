package com.tihomir.t212.tradingbot.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TradingAlgorithmTest {

  private final ThresholdTestAlgorithm algorithm = new ThresholdTestAlgorithm();

  @Test
  void fillsRequiredSettingsWithDefaultsAndLeavesOptionalOnesEmpty() {
    AlgorithmSettings settings = algorithm.resolveSettings(request(true, Map.of()));

    assertThat(settings.timePeriod()).isEqualTo(20);
    assertThat(settings.orderSizePercent()).isEqualByComparingTo("100");
    assertThat(settings.updateTimeSeconds()).isEmpty();
    assertThat(settings.stopLossPercent()).isEmpty();
    assertThat(settings.takeProfitPercent()).isEmpty();
    assertThat(settings.options().wholeNumber(ThresholdTestAlgorithm.WINDOW)).isEqualTo(5);
    assertThat(settings.options().optionalValue(ThresholdTestAlgorithm.THRESHOLD)).isEmpty();
  }

  @Test
  void skipsEveryCheckOfAnOptionalSettingThatIsNull() {
    Map<String, BigDecimal> values = new HashMap<>();
    values.put(ThresholdTestAlgorithm.THRESHOLD, null);
    values.put(ThresholdTestAlgorithm.WINDOW, BigDecimal.ONE);
    values.put(TradingAlgorithm.STOP_LOSS_PERCENT, null);
    values.put(TradingAlgorithm.TAKE_PROFIT_PERCENT, null);

    AlgorithmSettings settings = algorithm.resolveSettings(request(true, values));

    assertThat(settings.options().optionalValue(ThresholdTestAlgorithm.THRESHOLD)).isEmpty();
    assertThat(settings.takeProfitPercent()).isEmpty();
  }

  @Test
  void checksAnOptionalSettingOnceItHasAValue() {
    assertThatThrownBy(() ->
            algorithm.resolveSettings(
                request(true, Map.of(ThresholdTestAlgorithm.THRESHOLD, BigDecimal.TWO))))
        .isInstanceOf(InvalidAlgorithmSettingsException.class)
        .hasMessage("threshold must be between 0 and 1");
    assertThatThrownBy(() ->
            algorithm.resolveSettings(
                request(
                    true,
                    Map.of(
                        ThresholdTestAlgorithm.THRESHOLD,
                        new BigDecimal("0.5"),
                        ThresholdTestAlgorithm.WINDOW,
                        BigDecimal.TWO))))
        .isInstanceOf(InvalidAlgorithmSettingsException.class)
        .hasMessageContaining("window of at least 3");
  }

  @Test
  void refusesValuesOutOfRangeDecimalsInWholeNumbersAndUnknownNames() {
    assertThatThrownBy(() ->
            algorithm.resolveSettings(
                request(true, Map.of(TradingAlgorithm.TIME_PERIOD, BigDecimal.ZERO))))
        .isInstanceOf(InvalidAlgorithmSettingsException.class)
        .hasMessage("timePeriod must be between 1 and 1000, as a whole number");
    assertThatThrownBy(() ->
            algorithm.resolveSettings(
                request(true, Map.of(TradingAlgorithm.TIME_PERIOD, new BigDecimal("2.5")))))
        .isInstanceOf(InvalidAlgorithmSettingsException.class);
    assertThatThrownBy(
            () -> algorithm.resolveSettings(request(true, Map.of("speed", BigDecimal.ONE))))
        .isInstanceOf(InvalidAlgorithmSettingsException.class)
        .hasMessage("Unknown setting: speed");
  }

  @Test
  void needsAnUpdateTimeExactlyWhenNotLiveOnTick() {
    AlgorithmSettings onTimer =
        algorithm.resolveSettings(
            request(false, Map.of(TradingAlgorithm.UPDATE_TIME_SECONDS, BigDecimal.TEN)));
    assertThat(onTimer.updateTimeSeconds()).contains(10);

    assertThatThrownBy(() -> algorithm.resolveSettings(request(false, Map.of())))
        .isInstanceOf(InvalidAlgorithmSettingsException.class)
        .hasMessageContaining("required when liveOnTick is off");
    assertThatThrownBy(() ->
            algorithm.resolveSettings(
                request(true, Map.of(TradingAlgorithm.UPDATE_TIME_SECONDS, BigDecimal.TEN))))
        .isInstanceOf(InvalidAlgorithmSettingsException.class)
        .hasMessageContaining("empty when liveOnTick is on");
  }

  @Test
  void takesAStopLossAndATakeProfitEachOnItsOwn() {
    AlgorithmSettings stopOnly =
        algorithm.resolveSettings(
            request(true, Map.of(TradingAlgorithm.STOP_LOSS_PERCENT, BigDecimal.TWO)));
    AlgorithmSettings takeProfitOnly =
        algorithm.resolveSettings(
            request(true, Map.of(TradingAlgorithm.TAKE_PROFIT_PERCENT, new BigDecimal("3.00"))));

    assertThat(stopOnly.stopLossPercent()).contains(BigDecimal.TWO);
    assertThat(stopOnly.takeProfitPercent()).isEmpty();
    assertThat(takeProfitOnly.takeProfitPercent()).isEqualTo(Optional.of(new BigDecimal("3")));
    assertThat(takeProfitOnly.stopLossPercent()).isEmpty();
  }

  @Test
  void listsTheSharedSettingsBeforeItsOwn() {
    assertThat(algorithm.settingDefinitions().definitions())
        .extracting(AlgorithmSettingDefinition::name)
        .containsExactly(
            TradingAlgorithm.TIME_PERIOD,
            TradingAlgorithm.UPDATE_TIME_SECONDS,
            TradingAlgorithm.ORDER_SIZE_PERCENT,
            TradingAlgorithm.STOP_LOSS_PERCENT,
            TradingAlgorithm.TAKE_PROFIT_PERCENT,
            ThresholdTestAlgorithm.WINDOW,
            ThresholdTestAlgorithm.THRESHOLD);
  }

  private static AlgorithmSettingsRequest request(
      boolean liveOnTick, Map<String, BigDecimal> values) {
    return new AlgorithmSettingsRequest(
        AlgorithmType.SIMPLE_MOVING_AVERAGE, CandleInterval.FIVE_MINUTES, liveOnTick, values);
  }
}
