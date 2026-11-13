package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingDefinitionList;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import java.util.Iterator;
import java.util.List;

/** Gives the listed signals one per price, then HOLD, and runs a hook on every price. */
final class ScriptedTestAlgorithm extends TradingAlgorithm {

  private final List<TradingSignal> signals;
  private final Runnable onEveryPrice;

  ScriptedTestAlgorithm(List<TradingSignal> signals, Runnable onEveryPrice) {
    super(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        "test-scripted",
        "Scripted signals",
        AlgorithmSettingDefinitionList.NONE);
    this.signals = List.copyOf(signals);
    this.onEveryPrice = onEveryPrice;
  }

  @Override
  protected SignalGenerator newSignalGenerator(AlgorithmSettings settings) {
    Iterator<TradingSignal> remaining = signals.iterator();
    return candle -> nextSignal(remaining);
  }

  private TradingSignal nextSignal(Iterator<TradingSignal> remaining) {
    onEveryPrice.run();
    if (remaining.hasNext()) {
      return remaining.next();
    }
    return TradingSignal.HOLD;
  }
}
