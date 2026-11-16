package com.tihomir.t212.tradingbot.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.exception.AlgorithmSettingsNotFoundException;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlgorithmSettingsServiceTest {

  private static final long FIRST_ID = 100;
  private static final long UNKNOWN_ID = 42;

  private final AlgorithmSettingsService service =
      new AlgorithmSettingsService(
          new FakeAlgorithmStore(FIRST_ID), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

  @Test
  void savesIdenticalSettingsOnceAndReadsThemBack() {
    AlgorithmSettings settings = thresholdSettings();

    long algorithmId = service.saveSettings(settings);

    assertThat(algorithmId).isEqualTo(FIRST_ID);
    assertThat(service.saveSettings(settings)).isEqualTo(algorithmId);
    assertThat(service.getSettings(algorithmId)).isEqualTo(settings);
  }

  @Test
  void refusesAnUnknownId() {
    assertThatThrownBy(() -> service.getSettings(UNKNOWN_ID))
        .isInstanceOf(AlgorithmSettingsNotFoundException.class);
  }

  private static AlgorithmSettings thresholdSettings() {
    return new ThresholdTestAlgorithm()
        .resolveSettings(
            new AlgorithmSettingsRequest(
                AlgorithmType.SIMPLE_MOVING_AVERAGE, CandleInterval.ONE_HOUR, true, Map.of()));
  }
}
