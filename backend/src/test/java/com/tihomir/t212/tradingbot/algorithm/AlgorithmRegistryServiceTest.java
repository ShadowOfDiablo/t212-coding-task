package com.tihomir.t212.tradingbot.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlgorithmRegistryServiceTest {

  @Test
  void findsEveryAlgorithmByItsType() {
    SimpleMovingAverageAlgorithm movingAverage = new SimpleMovingAverageAlgorithm();
    AlgorithmRegistryService registry = new AlgorithmRegistryService(List.of(movingAverage));

    assertThat(registry.get(AlgorithmType.SIMPLE_MOVING_AVERAGE)).isSameAs(movingAverage);
    assertThat(registry.all().algorithms()).containsExactly(movingAverage);
  }

  @Test
  void refusesATypeWithoutAnAlgorithm() {
    AlgorithmRegistryService registry = new AlgorithmRegistryService(List.of());

    assertThatThrownBy(() -> registry.get(AlgorithmType.SIMPLE_MOVING_AVERAGE))
        .isInstanceOf(UnknownAlgorithmException.class);
  }

  @Test
  void refusesTwoAlgorithmsWithTheSameType() {
    assertThatThrownBy(() ->
            new AlgorithmRegistryService(
                List.of(new SimpleMovingAverageAlgorithm(), new ThresholdTestAlgorithm())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAnAlgorithmThatReusesASharedSettingName() {
    assertThatThrownBy(() -> new AlgorithmRegistryService(List.of(new SharedNameTestAlgorithm())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("shared setting");
  }
}
