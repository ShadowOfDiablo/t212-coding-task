package com.tihomir.t212.tradingbot.algorithm;

import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;

/**
 * The stateful signal logic of one run. It is given closed candles, and in live trading also the
 * still-forming candle, and never sees the account.
 */
public interface SignalGenerator {

  TradingSignal onPrice(CandleUpdate candle);
}
