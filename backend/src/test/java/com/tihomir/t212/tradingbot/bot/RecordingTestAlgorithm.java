package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingDefinitionList;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records every candle its generators see and signals BUY on each closed candle. */
public final class RecordingTestAlgorithm extends TradingAlgorithm {

  private final List<CandleUpdate> seenCandles = new CopyOnWriteArrayList<>();

  public RecordingTestAlgorithm() {
    super(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        "test-recording",
        "Recording",
        "A test algorithm.",
        AlgorithmSettingDefinitionList.NONE);
  }

  public List<CandleUpdate> seenCandles() {
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
