package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.SignalGenerator;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.OrderCheck;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.VirtualTradingGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays the candles of one training run, in one thread and without waiting. For each candle: the
 * stop loss, then the signal, then the order checks; a signal fills at the next candle's open, so
 * the run never looks ahead.
 */
final class TrainingReplay {

  static final int MAX_PORTFOLIO_POINTS_PER_RUN = 1000;

  private static final Logger log = LoggerFactory.getLogger(TrainingReplay.class);

  private static final int AMOUNT_SCALE = 8;
  private static final int MIN_POINT_STEP = 1;

  private final SignalGenerator signalGenerator;
  private final VirtualTradingGateway account;
  private final OrderCheckerService orderChecker;
  private final AlgorithmSettings settings;
  private BigDecimal lastValue = BigDecimal.ZERO;
  private int buyCount;
  private int sellCount;
  private int stopLossCount;
  private int refusedOrderCount;

  TrainingReplay(
      SignalGenerator signalGenerator,
      VirtualTradingGateway account,
      OrderCheckerService orderChecker,
      AlgorithmSettings settings) {
    this.signalGenerator = signalGenerator;
    this.account = account;
    this.orderChecker = orderChecker;
    this.settings = settings;
  }

  PortfolioPointList replay(List<CandleUpdate> candles) {
    int candleCount = candles.size();
    int lastIndex = candleCount - 1;
    int pointStep =
        Math.max(MIN_POINT_STEP, Math.ceilDiv(candleCount, MAX_PORTFOLIO_POINTS_PER_RUN));
    List<PortfolioPoint> points = new ArrayList<>();
    for (int index = 0; index < candleCount; index++) {
      CandleUpdate candle = candles.get(index);
      sellAtStopLossIfReached(candle);
      TradingSignal signal = signalGenerator.onPrice(candle);
      PortfolioPoint point = portfolioPointAt(candle);
      if (index % pointStep == 0 || index == lastIndex) {
        points.add(point);
      }
      if (signal != TradingSignal.HOLD) {
        nextCandle(candles, index).ifPresent(next -> actOnSignal(signal, next));
      }
    }
    return new PortfolioPointList(points);
  }

  BigDecimal finalValue() {
    return lastValue;
  }

  RunCounts counts() {
    return new RunCounts(buyCount, sellCount, stopLossCount, refusedOrderCount);
  }

  private PortfolioPoint portfolioPointAt(CandleUpdate candle) {
    BigDecimal holdingsValue = account.baseQuantity().multiply(candle.close());
    lastValue = account.cash().add(holdingsValue).setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
    return new PortfolioPoint(candle.closeTime(), account.cash(), lastValue);
  }

  /** A candle that opens below the stop fills at its open, like a stop order after a gap. */
  private void sellAtStopLossIfReached(CandleUpdate candle) {
    if (!orderChecker.isStopLossReached(account, candle.low(), settings)) {
      return;
    }
    BigDecimal stopPrice = orderChecker.stopPrice(account, settings).orElseThrow();
    BigDecimal fillPrice = stopPrice.min(candle.open());
    account.updateMarketPrice(fillPrice, candle.openTime());
    if (placeCheckedOrder(TradingSignal.SELL, fillPrice)) {
      sellCount++;
      stopLossCount++;
    }
  }

  private void actOnSignal(TradingSignal signal, CandleUpdate fillCandle) {
    account.updateMarketPrice(fillCandle.open(), fillCandle.openTime());
    if (!placeCheckedOrder(signal, fillCandle.open())) {
      return;
    }
    if (signal == TradingSignal.BUY) {
      buyCount++;
      return;
    }
    sellCount++;
  }

  private boolean placeCheckedOrder(TradingSignal signal, BigDecimal price) {
    OrderCheck check = orderChecker.checkOrder(signal, account, price, settings);
    if (!check.isApproved()) {
      log.debug("{}: training {} refused: {}", account.ticker(), signal, check.refusal());
      refusedOrderCount++;
      return false;
    }
    try {
      account.placeMarketOrder(check.order().orElseThrow());
      return true;
    } catch (ExchangeRejectedRequestException e) {
      log.debug("{}: training {} rejected: {}", account.ticker(), signal, e.getMessage());
      refusedOrderCount++;
      return false;
    }
  }

  private static Optional<CandleUpdate> nextCandle(List<CandleUpdate> candles, int index) {
    int nextIndex = index + 1;
    if (nextIndex >= candles.size()) {
      return Optional.empty();
    }
    return Optional.of(candles.get(nextIndex));
  }
}
