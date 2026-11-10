package com.tihomir.t212.tradingbot.marketdata;

import com.tihomir.t212.tradingbot.marketdata.stream.MarketDataStreamConnection;
import com.tihomir.t212.tradingbot.marketdata.stream.MarketDataStreamConnectionFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

public final class CandleFeeder implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(CandleFeeder.class);
  private static final int QUEUE_CAPACITY = 10_000;

  private final String name;
  private final ExchangeMarketDataProtocol protocol;
  private final List<String> tickers;
  private final CandleInterval interval;
  private final TickerPriceWriter priceWriter;
  private final MarketDataStreamConnectionFactory connectionFactory;
  private final RestClient restClient;

  private final List<CandleUpdateListener> listeners = new CopyOnWriteArrayList<>();
  private final BlockingQueue<CandleUpdate> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
  private final List<MarketDataStreamConnection> connections = new CopyOnWriteArrayList<>();
  private Thread dispatcher;
  private volatile boolean running;

  private final LongAdder messages = new LongAdder();
  private final LongAdder parseErrors = new LongAdder();
  private final LongAdder droppedUpdates = new LongAdder();
  private final AtomicLong parseNanosTotal = new AtomicLong();
  private final AtomicLong parseNanosMax = new AtomicLong();
  private final AtomicLong lagMillisTotal = new AtomicLong();
  private final AtomicLong lagMillisMax = new AtomicLong();
  private final LongAdder lagSamples = new LongAdder();

  public CandleFeeder(
      String name,
      ExchangeMarketDataProtocol protocol,
      List<String> tickers,
      CandleInterval interval,
      TickerPriceWriter priceWriter,
      MarketDataStreamConnectionFactory connectionFactory,
      RestClient restClient) {
    this.name = name;
    this.protocol = protocol;
    this.tickers = List.copyOf(tickers);
    this.interval = interval;
    this.priceWriter = priceWriter;
    this.connectionFactory = connectionFactory;
    this.restClient = restClient;
  }

  public void addListener(CandleUpdateListener listener) {
    listeners.add(listener);
  }

  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    dispatcher = new Thread(this::dispatchLoop, name + "-candle-dispatcher");
    dispatcher.setDaemon(true);
    dispatcher.start();
    try {
      openConnections();
    } catch (RuntimeException e) {
      stop();
      throw e;
    }
    log.info(
        "{}: streaming {} tickers over {} connection(s)", name, tickers.size(), connections.size());
  }

  private void openConnections() {
    for (List<String> streams : groupStreamsByConnection()) {
      connections.add(connectionFactory.open(protocol.connectionUri(streams), this::onMessage));
    }
  }

  public synchronized void stop() {
    if (!running) {
      return;
    }
    running = false;
    connections.forEach(MarketDataStreamConnection::close);
    connections.clear();
    dispatcher.interrupt();
  }

  @Override
  public void close() {
    stop();
  }

  public List<CandleUpdate> history(
      String ticker, CandleInterval historyInterval, Instant from, Instant to) {
    List<CandleUpdate> candles = new ArrayList<>();
    int maxCandlesPerRequest = protocol.maxCandlesPerRestHistoryRequest();
    Instant cursor = from;
    while (cursor.isBefore(to)) {
      String body =
          restClient
              .get()
              .uri(protocol.candlesUri(ticker, historyInterval, cursor, to, maxCandlesPerRequest))
              .retrieve()
              .body(String.class);
      List<CandleUpdate> page = protocol.parseCandles(body, ticker, historyInterval);
      candles.addAll(page);
      if (page.size() < maxCandlesPerRequest) {
        break;
      }
      cursor = page.getLast().openTime().plus(historyInterval.duration());
    }
    return candles;
  }

  public CandleFeederStatistics statistics() {
    long count = messages.sum();
    long samples = lagSamples.sum();
    int open = (int) connections.stream().filter(MarketDataStreamConnection::isOpen).count();
    return new CandleFeederStatistics(
        count,
        parseErrors.sum(),
        droppedUpdates.sum(),
        queue.size(),
        average(parseNanosTotal.get(), count) / 1_000,
        parseNanosMax.get() / 1_000,
        average(lagMillisTotal.get(), samples),
        lagMillisMax.get(),
        connections.size(),
        open);
  }

  List<List<String>> groupStreamsByConnection() {
    int streamsPerTicker = protocol.streamNames(tickers.getFirst(), interval).size();
    int tickersPerConnection = Math.max(1, protocol.maxStreamsPerConnection() / streamsPerTicker);
    int tickerCount = tickers.size();
    List<List<String>> streamGroups = new ArrayList<>();
    for (int start = 0; start < tickerCount; start += tickersPerConnection) {
      int end = Math.min(start + tickersPerConnection, tickerCount);
      List<String> tickerGroup = tickers.subList(start, end);
      streamGroups.add(streamNamesFor(tickerGroup));
    }
    return streamGroups;
  }

  private List<String> streamNamesFor(List<String> tickerGroup) {
    List<String> streams = new ArrayList<>();
    for (String ticker : tickerGroup) {
      streams.addAll(protocol.streamNames(ticker, interval));
    }
    return streams;
  }

  void onMessage(String message, Instant receivedAt) {
    long start = System.nanoTime();
    Optional<MarketDataEvent> event;
    try {
      event = protocol.parse(message);
    } catch (RuntimeException e) {
      parseErrors.increment();
      log.debug("{}: unreadable message: {}", name, message, e);
      return;
    }
    long parseNanos = System.nanoTime() - start;
    messages.increment();
    parseNanosTotal.addAndGet(parseNanos);
    parseNanosMax.accumulateAndGet(parseNanos, Math::max);
    event.ifPresent(e -> applyMarketDataEvent(e, receivedAt));
  }

  private void applyMarketDataEvent(MarketDataEvent event, Instant receivedAt) {
    switch (event) {
      case CandleUpdate candle -> applyCandleUpdate(candle, receivedAt);
      case BestBidAskUpdate bestBidAsk -> applyBestBidAskUpdate(bestBidAsk, receivedAt);
    }
  }

  private void applyCandleUpdate(CandleUpdate candle, Instant receivedAt) {
    priceWriter.updateLastPrice(
        candle.ticker(), candle.close(), candle.eventTime(), TickerQuoteSource.WEBSOCKET);
    recordLag(Duration.between(candle.eventTime(), receivedAt).toMillis());
    if (!queue.offer(candle)) {
      droppedUpdates.increment();
    }
  }

  private void applyBestBidAskUpdate(BestBidAskUpdate bestBidAsk, Instant receivedAt) {
    priceWriter.updateBidAsk(
        bestBidAsk.ticker(),
        bestBidAsk.bid(),
        bestBidAsk.ask(),
        receivedAt,
        TickerQuoteSource.WEBSOCKET);
  }

  private static long average(long total, long count) {
    if (count == 0) {
      return 0;
    }
    return total / count;
  }

  private void recordLag(long lagMillis) {
    lagSamples.increment();
    lagMillisTotal.addAndGet(lagMillis);
    lagMillisMax.accumulateAndGet(lagMillis, Math::max);
  }

  private void dispatchLoop() {
    while (running) {
      CandleUpdate update;
      try {
        update = queue.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      for (CandleUpdateListener listener : listeners) {
        try {
          listener.onCandleUpdate(update);
        } catch (RuntimeException e) {
          log.warn("{}: listener failed for {}", name, update.ticker(), e);
        }
      }
    }
  }
}
