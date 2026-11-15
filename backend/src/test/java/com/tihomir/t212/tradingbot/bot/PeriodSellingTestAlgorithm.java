package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingDefinitionList;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BUY on the first price, SELL on price number {@code timePeriod}, so the best time period is the
 * one that sells at the top. Generators for a time period above {@code maxWorkingTimePeriod} fail.
 * Records the settings of every generator.
 */
final class PeriodSellingTestAlgorithm extends TradingAlgorithm {

  private static final int FIRST_PRICE = 1;

  private final int maxWorkingTimePeriod;
  private final Runnable onEveryGenerator;
  private final List<AlgorithmSettings> seenSettings = new CopyOnWriteArrayList<>();

  PeriodSellingTestAlgorithm(int maxWorkingTimePeriod) {
    this(maxWorkingTimePeriod, () -> {});
  }

  PeriodSellingTestAlgorithm(int maxWorkingTimePeriod, Runnable onEveryGenerator) {
    super(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        "test-period-selling",
        "Sells after timePeriod prices",
        AlgorithmSettingDefinitionList.NONE);
    this.maxWorkingTimePeriod = maxWorkingTimePeriod;
    this.onEveryGenerator = onEveryGenerator;
  }

  List<AlgorithmSettings> seenSettings() {
    return List.copyOf(seenSettings);
  }

  @Override
  protected SignalGenerator newSignalGenerator(AlgorithmSettings settings) {
    seenSettings.add(settings);
    onEveryGenerator.run();
    if (settings.timePeriod() > maxWorkingTimePeriod) {
      throw new IllegalStateException("Test failure for time period " + settings.timePeriod());
    }
    AtomicInteger priceCount = new AtomicInteger();
    return candle -> signalAt(priceCount.incrementAndGet(), settings.timePeriod());
  }

  private static TradingSignal signalAt(int priceNumber, int timePeriod) {
    if (priceNumber == FIRST_PRICE) {
      return TradingSignal.BUY;
    }
    if (priceNumber == timePeriod) {
      return TradingSignal.SELL;
    }
    return TradingSignal.HOLD;
  }
}
