package com.tihomir.t212.tradingbot.marketdata.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CandleIntervalTest {

  @Test
  void writesEachIntervalAsAShortCodeAndReadsItBack() {
    assertThat(Arrays.stream(CandleInterval.values()).map(CandleInterval::code))
        .containsExactly("1s", "1m", "5m", "15m", "1h", "4h", "1d");
    assertThat(CandleInterval.values())
        .allSatisfy(
            interval -> assertThat(CandleInterval.fromCode(interval.code())).isEqualTo(interval));
  }

  @Test
  void refusesAnUnknownCode() {
    assertThatThrownBy(() -> CandleInterval.fromCode("FIVE_MINUTES"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
