package com.tihomir.t212.tradingbot.algorithm.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SimpleMovingAverageTest {

  @Test
  void isEmptyUntilThePeriodIsFilledThenAveragesTheLastValues() {
    SimpleMovingAverage average = new SimpleMovingAverage(3);

    average.addValue(new BigDecimal("1"));
    average.addValue(new BigDecimal("2"));
    assertThat(average.currentValue()).isEmpty();

    average.addValue(new BigDecimal("6"));
    assertThat(average.currentValue()).hasValueSatisfying(value -> assertEquals(value, "3"));

    average.addValue(new BigDecimal("10"));
    assertThat(average.currentValue()).hasValueSatisfying(value -> assertEquals(value, "6"));
  }

  @Test
  void keepsDecimalsOfAnUnevenAverage() {
    SimpleMovingAverage average = new SimpleMovingAverage(3);
    average.addValue(BigDecimal.ONE);
    average.addValue(BigDecimal.ONE);
    average.addValue(BigDecimal.ZERO);

    assertThat(average.currentValue())
        .hasValueSatisfying(value -> assertEquals(value, "0.6666666666666667"));
  }

  @Test
  void refusesAPeriodBelowOne() {
    assertThatThrownBy(() -> new SimpleMovingAverage(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static void assertEquals(BigDecimal actual, String expected) {
    assertThat(actual).isEqualByComparingTo(expected);
  }
}
