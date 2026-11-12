package com.tihomir.t212.tradingbot.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlgorithmRegistryServiceTest {

  @Test
  void findsEveryAlgorithmByItsId() {
    SimpleMovingAverageAlgorithm movingAverage = new SimpleMovingAverageAlgorithm();
    ThresholdTestAlgorithm threshold = new ThresholdTestAlgorithm();
    AlgorithmRegistryService registry =
        new AlgorithmRegistryService(List.of(movingAverage, threshold));

    assertThat(registry.get(SimpleMovingAverageAlgorithm.ID)).isSameAs(movingAverage);
    assertThat(registry.all().algorithms()).containsExactly(movingAverage, threshold);
    assertThatThrownBy(() -> registry.get("missing")).isInstanceOf(UnknownAlgorithmException.class);
  }

  @Test
  void refusesTwoAlgorithmsWithTheSameId() {
    assertThatThrownBy(() ->
            new AlgorithmRegistryService(
                List.of(new SimpleMovingAverageAlgorithm(), new SimpleMovingAverageAlgorithm())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAnAlgorithmThatReusesASharedSettingName() {
    assertThatThrownBy(() -> new AlgorithmRegistryService(List.of(new SharedNameTestAlgorithm())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("shared setting");
  }
}
