package com.tihomir.t212.tradingbot.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlgorithmSettingsServiceTest {

  private static final long THRESHOLD_FIRST_ID = 100;

  private final AlgorithmSettingsService service =
      new AlgorithmSettingsService(
          List.of(
              new FakeAlgorithmOptionStore(SimpleMovingAverageAlgorithm.ID, 1),
              new FakeAlgorithmOptionStore(ThresholdTestAlgorithm.ID, THRESHOLD_FIRST_ID)));

  @Test
  void savesThroughTheStoreOfTheSettingsAlgorithmAndReadsThemBack() {
    AlgorithmSettings settings = thresholdSettings();

    long settingsId = service.saveSettings(settings);

    assertThat(settingsId).isEqualTo(THRESHOLD_FIRST_ID);
    assertThat(service.saveSettings(settings)).isEqualTo(settingsId);
    assertThat(service.getSettings(settingsId)).isEqualTo(settings);
  }

  @Test
  void refusesSettingsOfAnAlgorithmWithoutAStoreAndUnknownIds() {
    AlgorithmSettings settings = thresholdSettings();
    AlgorithmSettingsService withoutThresholdStore =
        new AlgorithmSettingsService(
            List.of(new FakeAlgorithmOptionStore(SimpleMovingAverageAlgorithm.ID, 1)));

    assertThatThrownBy(() -> withoutThresholdStore.saveSettings(settings))
        .isInstanceOf(UnknownAlgorithmException.class);
    assertThatThrownBy(() -> service.getSettings(42))
        .isInstanceOf(AlgorithmSettingsNotFoundException.class);
  }

  @Test
  void refusesTwoStoresForOneAlgorithm() {
    assertThatThrownBy(() ->
            new AlgorithmSettingsService(
                List.of(
                    new FakeAlgorithmOptionStore(SimpleMovingAverageAlgorithm.ID, 1),
                    new FakeAlgorithmOptionStore(SimpleMovingAverageAlgorithm.ID, 2))))
        .isInstanceOf(IllegalStateException.class);
  }

  private static AlgorithmSettings thresholdSettings() {
    return new ThresholdTestAlgorithm()
        .resolveSettings(
            new AlgorithmSettingsRequest(
                ThresholdTestAlgorithm.ID, CandleInterval.ONE_HOUR, true, Map.of()));
  }
}
