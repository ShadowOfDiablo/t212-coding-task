package com.tihomir.t212.tradingbot.marketdata.history;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleList;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.order.exception.TickerNotTradedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CandleHistoryService {

  static final long MAX_CANDLES_PER_TRAINING_RUN = 1_000_000;

  private static final Logger log = LoggerFactory.getLogger(CandleHistoryService.class);

  private final BrokerRegistryService brokers;
  private final CandleStore candleStore;
  private final Clock clock;

  public CandleHistoryService(BrokerRegistryService brokers, CandleStore candleStore, Clock clock) {
    this.brokers = brokers;
    this.candleStore = candleStore;
    this.clock = clock;
  }

  /**
   * Returns the closed candles of the range, downloading and storing the ones that are missing.
   * Runs one request at a time, so two downloads never store the same page at once.
   *
   * @throws CandleRangeTooLargeException if the range holds too many candles
   * @throws CandleDownloadFailedException if a page could not be downloaded; the pages stored
   *     before it are kept
   */
  public synchronized CandleList getCandles(CandleHistoryRequest request) {
    Broker broker = tradingBroker(request);
    checkCandleCount(request);
    Instant lastClosedCandleEnd = lastClosedCandleEnd(request.interval());
    if (startsAfterLastClosedCandle(request, lastClosedCandleEnd)) {
      return new CandleList(List.of());
    }
    CandleHistoryRequest closedRequest =
        request.endingAt(Collections.min(List.of(request.to(), lastClosedCandleEnd)));
    long tickerId = tickerId(closedRequest);
    Optional<StoredCandleRange> storedRange =
        candleStore.getStoredCandleRange(tickerId, closedRequest.interval());
    downloadMissingCandles(broker.candleFeeder(), tickerId, closedRequest, storedRange);
    return candleStore.getCandles(closedRequest);
  }

  private Broker tradingBroker(CandleHistoryRequest request) {
    return tradingBroker(request.broker(), request.ticker());
  }

  /**
   * The broker, after checking it exists and trades the ticker.
   *
   * @throws BrokerNotFoundException if there is no such broker
   * @throws TickerNotTradedException if the broker doesn't trade the ticker
   */
  public Broker tradingBroker(String brokerName, String ticker) {
    Broker broker =
        brokers.get(brokerName).orElseThrow(() -> new BrokerNotFoundException(brokerName));
    if (!broker.tickers().contains(ticker)) {
      throw new TickerNotTradedException(brokerName, ticker);
    }
    return broker;
  }

  private void checkCandleCount(CandleHistoryRequest request) {
    long requestedCandles =
        Duration.between(request.from(), request.to()).dividedBy(request.interval().duration());
    if (requestedCandles > MAX_CANDLES_PER_TRAINING_RUN) {
      throw new CandleRangeTooLargeException(requestedCandles, MAX_CANDLES_PER_TRAINING_RUN);
    }
  }

  private Instant lastClosedCandleEnd(CandleInterval interval) {
    long intervalMillis = interval.duration().toMillis();
    long nowMillis = clock.millis();
    return Instant.ofEpochMilli(nowMillis - nowMillis % intervalMillis);
  }

  private static boolean startsAfterLastClosedCandle(
      CandleHistoryRequest request, Instant lastClosedCandleEnd) {
    return !request.from().isBefore(lastClosedCandleEnd);
  }

  private long tickerId(CandleHistoryRequest request) {
    return candleStore
        .getTickerId(request.broker(), request.ticker())
        .orElseGet(() -> candleStore.insertTicker(request.broker(), request.ticker()));
  }

  private void downloadMissingCandles(
      CandleFeeder feeder,
      long tickerId,
      CandleHistoryRequest request,
      Optional<StoredCandleRange> storedRange) {
    if (storedRange.isEmpty()) {
      downloadForward(feeder, tickerId, request, request.from());
      return;
    }
    if (storedRange.get().startsAfter(request.from())) {
      downloadBackward(feeder, tickerId, request, storedRange.get().firstOpenTime());
    }
    if (storedRange.get().endsBefore(request.to())) {
      downloadForward(feeder, tickerId, request, storedRange.get().endTime());
    }
  }

  /** Pages forward from {@code start}, each page continuing right after the last stored candle. */
  private void downloadForward(
      CandleFeeder feeder, long tickerId, CandleHistoryRequest request, Instant start) {
    Instant pageStart = start;
    int storedPages = 0;
    while (pageStart.isBefore(request.to())) {
      List<CandleUpdate> candles = historyPage(feeder, request, pageStart, request.to()).candles();
      if (candles.isEmpty()) {
        break;
      }
      candleStore.insertCandlePage(tickerId, new CandleList(candles));
      storedPages++;
      if (candles.size() < feeder.maxCandlesPerHistoryPage()) {
        break;
      }
      pageStart = candles.getLast().openTime().plus(request.interval().duration());
    }
    logStoredPages(request, storedPages, start, request.to());
  }

  /**
   * Pages backward from the first stored candle, so the stored candles stay one block even when a
   * download stops half way.
   */
  private void downloadBackward(
      CandleFeeder feeder, long tickerId, CandleHistoryRequest request, Instant end) {
    Duration pageSpan =
        request.interval().duration().multipliedBy(feeder.maxCandlesPerHistoryPage());
    Instant pageEnd = end;
    int storedPages = 0;
    while (pageEnd.isAfter(request.from())) {
      Instant pageStart = Collections.max(List.of(request.from(), pageEnd.minus(pageSpan)));
      CandleList page = historyPage(feeder, request, pageStart, pageEnd);
      if (page.candles().isEmpty()) {
        break;
      }
      candleStore.insertCandlePage(tickerId, page);
      storedPages++;
      pageEnd = pageStart;
    }
    logStoredPages(request, storedPages, request.from(), end);
  }

  private static CandleList historyPage(
      CandleFeeder feeder, CandleHistoryRequest request, Instant pageStart, Instant pageEnd) {
    try {
      return feeder.historyPage(request.ticker(), request.interval(), pageStart, pageEnd);
    } catch (RuntimeException e) {
      log.error(
          "{} {} {}: downloading candles from {} failed",
          request.broker(),
          request.ticker(),
          request.interval(),
          pageStart,
          e);
      throw new CandleDownloadFailedException(request, pageStart, e);
    }
  }

  private static void logStoredPages(
      CandleHistoryRequest request, int storedPages, Instant from, Instant to) {
    log.info(
        "{} {} {}: stored {} page(s) of candles from {} to {}",
        request.broker(),
        request.ticker(),
        request.interval(),
        storedPages,
        from,
        to);
  }
}
