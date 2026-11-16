package com.tihomir.t212.tradingbot.bot.live;

import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryRequest;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feeds one signal generator with live prices. Closed candles of the timeframe come from the candle
 * store, once their period has ended; the live price is added on top as the forming candle.
 */
final class LiveSignalSource {

  private static final Logger log = LoggerFactory.getLogger(LiveSignalSource.class);

  private final CandleHistoryService candleHistory;
  private final String broker;
  private final String ticker;
  private final TradingAlgorithm algorithm;
  private final AlgorithmSettings settings;
  private final SignalGenerator generator;
  private final Duration timeframe;
  private Instant nextClosedOpenTime = Instant.EPOCH;

  LiveSignalSource(
      CandleHistoryService candleHistory,
      String broker,
      String ticker,
      TradingAlgorithm algorithm,
      AlgorithmSettings settings) {
    this.candleHistory = candleHistory;
    this.broker = broker;
    this.ticker = ticker;
    this.algorithm = algorithm;
    this.settings = settings;
    this.generator = algorithm.createSignalGenerator(settings);
    this.timeframe = settings.timeframe().duration();
  }

  String ticker() {
    return ticker;
  }

  AlgorithmSettings settings() {
    return settings;
  }

  /**
   * Gives the generator the closed candles it needs before its first signal. Their signals are
   * ignored: the bot only acts from now on.
   *
   * @throws RuntimeException if the candles can't be read; the source is then unusable
   */
  void warmUp(Instant now) {
    Instant currentOpenTime = periodOpenTime(now);
    Instant from = currentOpenTime.minus(timeframe.multipliedBy(algorithm.warmUpCandles(settings)));
    List<CandleUpdate> candles = closedCandles(from, currentOpenTime);
    candles.forEach(generator::onPrice);
    nextClosedOpenTime = currentOpenTime;
    log.info(
        "{} {}: live {} warmed up with {} {} candle(s)",
        broker,
        ticker,
        algorithm.type(),
        candles.size(),
        settings.timeframe());
  }

  /** The signal at this live price, or of a candle that closed since the last price. */
  TradingSignal signalAt(BigDecimal price, Instant time) {
    Instant currentOpenTime = periodOpenTime(time);
    TradingSignal closedCandleSignal = signalOfNewClosedCandles(currentOpenTime);
    TradingSignal formingCandleSignal =
        generator.onPrice(formingCandle(price, time, currentOpenTime));
    if (formingCandleSignal != TradingSignal.HOLD) {
      return formingCandleSignal;
    }
    return closedCandleSignal;
  }

  /** A failed read is retried at the next price, so no closed candle is skipped. */
  private TradingSignal signalOfNewClosedCandles(Instant currentOpenTime) {
    if (!currentOpenTime.isAfter(nextClosedOpenTime)) {
      return TradingSignal.HOLD;
    }
    List<CandleUpdate> candles;
    try {
      candles = closedCandles(nextClosedOpenTime, currentOpenTime);
    } catch (RuntimeException e) {
      log.warn(
          "{} {}: reading the closed {} candles from {} failed, retrying at the next price: {}",
          broker,
          ticker,
          settings.timeframe(),
          nextClosedOpenTime,
          e.getMessage());
      return TradingSignal.HOLD;
    }
    TradingSignal signal = TradingSignal.HOLD;
    for (CandleUpdate candle : candles) {
      TradingSignal candleSignal = generator.onPrice(candle);
      nextClosedOpenTime = candle.openTime().plus(timeframe);
      if (candleSignal != TradingSignal.HOLD) {
        signal = candleSignal;
      }
    }
    return signal;
  }

  private List<CandleUpdate> closedCandles(Instant from, Instant to) {
    return candleHistory
        .getCandles(new CandleHistoryRequest(broker, ticker, settings.timeframe(), from, to))
        .candles();
  }

  private CandleUpdate formingCandle(BigDecimal price, Instant time, Instant openTime) {
    return new CandleUpdate(
        ticker,
        settings.timeframe(),
        openTime,
        settings.timeframe().closeTime(openTime),
        price,
        price,
        price,
        price,
        BigDecimal.ZERO,
        false,
        time);
  }

  private Instant periodOpenTime(Instant time) {
    long timeframeMillis = timeframe.toMillis();
    long timeMillis = time.toEpochMilli();
    return Instant.ofEpochMilli(timeMillis - timeMillis % timeframeMillis);
  }
}
