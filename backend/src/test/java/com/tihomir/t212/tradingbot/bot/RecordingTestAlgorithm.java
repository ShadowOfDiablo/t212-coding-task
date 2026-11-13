package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingDefinitionList;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records every candle its generators see and signals BUY on each closed candle. */
final class RecordingTestAlgorithm extends TradingAlgorithm {

  static final int ID = 104;

  private final List<CandleUpdate> seenCandles = new CopyOnWriteArrayList<>();

  RecordingTestAlgorithm() {
    super(ID, "test-recording", "Recording", AlgorithmSettingDefinitionList.NONE);
  }

  List<CandleUpdate> seenCandles() {
    return List.copyOf(seenCandles);
  }

  @Override
  protected SignalGenerator newSignalGenerator(AlgorithmSettings settings) {
    return this::recordCandle;
  }

  private TradingSignal recordCandle(CandleUpdate candle) {
    seenCandles.add(candle);
    if (candle.closed()) {
      return TradingSignal.BUY;
    }
    return TradingSignal.HOLD;
  }
}
