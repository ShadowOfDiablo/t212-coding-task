package com.tihomir.t212.tradingbot.algorithm.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.FakeAlgorithmStore;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.exception.SavedAlgorithmNotFoundException;
import com.tihomir.t212.tradingbot.algorithm.exception.SettingsAlreadySavedException;
import com.tihomir.t212.tradingbot.algorithm.indicator.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SavedAlgorithmServiceTest {

  private static final long FIRST_ALGORITHM_ID = 100;
  private static final long UNKNOWN_ID = 42;
  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final String NAME = "BTC hourly";
  private static final String PADDED_NAME = "  " + NAME + "  ";
  private static final String NEW_NAME = "BTC hourly, tuned";
  private static final BigDecimal TIME_PERIOD = new BigDecimal("135");
  private static final BigDecimal OTHER_TIME_PERIOD = new BigDecimal("50");

  private final FakeAlgorithmStore algorithmStore = new FakeAlgorithmStore(FIRST_ALGORITHM_ID);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final SavedAlgorithmService service =
      new SavedAlgorithmService(
          new AlgorithmRegistryService(List.of(new SimpleMovingAverageAlgorithm())),
          new AlgorithmSettingsService(algorithmStore, clock),
          new FakeSavedAlgorithmStore(algorithmStore),
          clock);

  @Test
  void savesCheckedSettingsUnderATrimmedName() {
    SavedAlgorithm saved = service.saveAlgorithm(PADDED_NAME, request());

    assertThat(saved.name()).isEqualTo(NAME);
    assertThat(saved.savedAt()).isEqualTo(NOW);
    assertThat(saved.algorithmId()).isEqualTo(FIRST_ALGORITHM_ID);
    assertThat(saved.settings().timePeriod()).isEqualTo(TIME_PERIOD.intValueExact());
    assertThat(saved.settings().orderSizePercent()).isPositive();
  }

  @Test
  void renamesTheSameSettingsInsteadOfSavingThemTwice() {
    SavedAlgorithm first = service.saveAlgorithm(NAME, request());

    SavedAlgorithm second = service.saveAlgorithm(NEW_NAME, request());

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(service.getSavedAlgorithms().savedAlgorithms())
        .singleElement()
        .extracting(SavedAlgorithm::name)
        .isEqualTo(NEW_NAME);
  }

  @Test
  void deletesAnEntryAndRefusesAnUnknownOne() {
    SavedAlgorithm saved = service.saveAlgorithm(NAME, request());

    service.deleteSavedAlgorithm(saved.id());

    assertThat(service.getSavedAlgorithms().savedAlgorithms()).isEmpty();
    assertThatThrownBy(() -> service.deleteSavedAlgorithm(UNKNOWN_ID))
        .isInstanceOf(SavedAlgorithmNotFoundException.class);
  }

  @Test
  void replacesTheSettingsOfAnEntryAndKeepsItsName() {
    SavedAlgorithm saved = service.saveAlgorithm(NAME, request());

    SavedAlgorithm replaced = service.replaceSettings(saved.id(), request(OTHER_TIME_PERIOD));

    assertThat(replaced.id()).isEqualTo(saved.id());
    assertThat(replaced.name()).isEqualTo(NAME);
    assertThat(replaced.algorithmId()).isNotEqualTo(saved.algorithmId());
    assertThat(replaced.settings().timePeriod()).isEqualTo(OTHER_TIME_PERIOD.intValueExact());
  }

  @Test
  void refusesToReplaceWithSettingsSavedUnderAnotherEntry() {
    SavedAlgorithm first = service.saveAlgorithm(NAME, request());
    service.saveAlgorithm(NEW_NAME, request(OTHER_TIME_PERIOD));

    assertThatThrownBy(() -> service.replaceSettings(first.id(), request(OTHER_TIME_PERIOD)))
        .isInstanceOf(SettingsAlreadySavedException.class)
        .hasMessageContaining(NEW_NAME);
    assertThatThrownBy(() -> service.replaceSettings(UNKNOWN_ID, request()))
        .isInstanceOf(SavedAlgorithmNotFoundException.class);
  }

  private static AlgorithmSettingsRequest request() {
    return request(TIME_PERIOD);
  }

  private static AlgorithmSettingsRequest request(BigDecimal timePeriod) {
    return new AlgorithmSettingsRequest(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        CandleInterval.ONE_HOUR,
        true,
        Map.of(TradingAlgorithm.TIME_PERIOD, timePeriod));
  }
}
