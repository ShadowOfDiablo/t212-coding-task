package com.tihomir.t212.tradingbot.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleMovingAverageAlgorithmTest {

  private static final int TIME_PERIOD = 3;

  private final SimpleMovingAverageAlgorithm algorithm = new SimpleMovingAverageAlgorithm();

  @Test
  void buysWhenThePriceMovesBelowTheAverageAndSellsWhenItMovesAbove() {
    SignalGenerator generator = generator();

    assertThat(closedSignals(generator, 10, 10, 10, 13, 8, 7, 12, 14))
        .containsExactly(
            TradingSignal.HOLD,
            TradingSignal.HOLD,
            TradingSignal.HOLD,
            TradingSignal.HOLD,
            TradingSignal.BUY,
            TradingSignal.HOLD,
            TradingSignal.SELL,
            TradingSignal.HOLD);
  }

  @Test
  void keepsItsSideWhileThePriceSitsOnTheAverage() {
    SignalGenerator generator = generator();

    assertThat(closedSignals(generator, 10, 10, 13, 11.5, 9))
        .containsExactly(
            TradingSignal.HOLD,
            TradingSignal.HOLD,
            TradingSignal.HOLD,
            TradingSignal.HOLD,
            TradingSignal.BUY);
  }

  @Test
  void comparesAFormingCandleWithTheAverageOfTheClosedOnesWithoutAddingIt() {
    SignalGenerator generator = generator();
    closedSignals(generator, 10, 10, 13);

    assertThat(generator.onPrice(candle(5, false))).isEqualTo(TradingSignal.BUY);
    assertThat(generator.onPrice(candle(20, false))).isEqualTo(TradingSignal.SELL);
    assertThat(generator.onPrice(candle(12, true))).isEqualTo(TradingSignal.HOLD);
  }

  @Test
  void warmsUpOverItsTimePeriod() {
    AlgorithmSettings settings = settings();

    assertThat(algorithm.warmUpCandles(settings)).isEqualTo(TIME_PERIOD);
    assertThat(algorithm.settingDefinitions().definitions())
        .hasSize(TradingAlgorithm.SHARED_DEFINITIONS.definitions().size());
  }

  private SignalGenerator generator() {
    return algorithm.createSignalGenerator(settings());
  }

  private AlgorithmSettings settings() {
    return algorithm.resolveSettings(
        new AlgorithmSettingsRequest(
            AlgorithmType.SIMPLE_MOVING_AVERAGE,
            CandleInterval.ONE_MINUTE,
            true,
            Map.of(TradingAlgorithm.TIME_PERIOD, BigDecimal.valueOf(TIME_PERIOD))));
  }

  private static List<TradingSignal> closedSignals(SignalGenerator generator, double... closes) {
    return Arrays.stream(closes).mapToObj(close -> generator.onPrice(candle(close, true))).toList();
  }

  private static CandleUpdate candle(double close, boolean closed) {
    BigDecimal price = BigDecimal.valueOf(close);
    return new CandleUpdate(
        "BTCUSDT",
        CandleInterval.ONE_MINUTE,
        Instant.EPOCH,
        Instant.EPOCH,
        price,
        price,
        price,
        price,
        BigDecimal.ONE,
        closed,
        Instant.EPOCH);
  }
}
