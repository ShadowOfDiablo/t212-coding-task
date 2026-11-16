package com.tihomir.t212.tradingbot.bot.run;

import com.tihomir.t212.tradingbot.bot.exception.RunNotFoundException;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryRequest;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/** The price of a run's ticker over the run, for the chart its positions are drawn on. */
@Service
public class RunPriceService {

  static final int MAX_PRICE_POINTS = 1_000;

  private final AlgorithmRunService runService;
  private final CandleHistoryService candleHistory;
  private final Clock clock;

  public RunPriceService(
      AlgorithmRunService runService, CandleHistoryService candleHistory, Clock clock) {
    this.runService = runService;
    this.candleHistory = candleHistory;
    this.clock = clock;
  }

  /**
   * Closing prices of the run's ticker on its timeframe, over the replayed range of a training run
   * or from start to end (or now) of a live run, thinned evenly to at most {@value
   * #MAX_PRICE_POINTS} points; the last candle is always kept.
   *
   * @throws RunNotFoundException if the broker has no run with this id
   */
  public PricePointList getRunPrices(String broker, long runId) {
    RunDetails details = runService.getRun(broker, runId);
    RecordedRun run = details.run();
    Instant from = run.from().orElse(run.startedAt());
    Instant to = run.to().or(run::finishedAt).orElseGet(clock::instant);
    CandleList candles =
        candleHistory.getCandles(
            new CandleHistoryRequest(
                broker, run.ticker(), details.settings().timeframe(), from, to));
    return new PricePointList(thinned(candles.candles()));
  }

  static List<PricePoint> thinned(List<CandleUpdate> candles) {
    int step = Math.ceilDiv(candles.size(), MAX_PRICE_POINTS);
    int lastIndex = candles.size() - 1;
    return IntStream.rangeClosed(0, lastIndex)
        .filter(index -> index % step == 0 || index == lastIndex)
        .mapToObj(candles::get)
        .map(candle -> new PricePoint(candle.openTime(), candle.close()))
        .toList();
  }
}
