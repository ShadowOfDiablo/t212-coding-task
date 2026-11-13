package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingDefinitionList;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import java.util.Iterator;
import java.util.List;

/** Gives the listed signals one per price, then HOLD, and runs a hook on every price. */
final class ScriptedTestAlgorithm extends TradingAlgorithm {

  static final int ID = 103;

  private final List<TradingSignal> signals;
  private final Runnable onEveryPrice;

  ScriptedTestAlgorithm(List<TradingSignal> signals, Runnable onEveryPrice) {
    super(ID, "test-scripted", "Scripted signals", AlgorithmSettingDefinitionList.NONE);
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
