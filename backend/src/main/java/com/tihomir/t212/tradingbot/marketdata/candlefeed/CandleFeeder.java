package com.tihomir.t212.tradingbot.marketdata.candlefeed;

import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceWriter;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuoteSource;
import com.tihomir.t212.tradingbot.marketdata.update.BidAskUpdate;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.marketdata.update.MarketDataEvent;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnection;
import com.tihomir.t212.tradingbot.marketdata.websocket.MarketDataStreamConnectionFactory;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

public final class CandleFeeder implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(CandleFeeder.class);
  private static final int QUEUE_CAPACITY = 10_000;
  private static final int MIN_TICKERS_PER_CONNECTION = 1;

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
  private volatile Duration exchangeClockOffset = Duration.ZERO;

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
    exchangeClockOffset = measureExchangeClockOffset();
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

  private Duration measureExchangeClockOffset() {
    try {
      Instant requestSent = Instant.now();
      String body = restClient.get().uri(protocol.serverTimeUri()).retrieve().body(String.class);
      Instant responseReceived = Instant.now();
      Instant serverTime = protocol.parseServerTime(body);
      Duration halfRoundTrip = Duration.between(requestSent, responseReceived).dividedBy(2);
      Duration offset = Duration.between(requestSent.plus(halfRoundTrip), serverTime);
      log.info("{}: exchange clock is {} ms ahead of the local clock", name, offset.toMillis());
      return offset;
    } catch (RuntimeException e) {
      log.warn(
          "{}: could not read the exchange clock, event times use the local clock: {}",
          name,
          e.toString());
      return Duration.ZERO;
    }
  }

  private void openConnections() {
    for (URI connectionUri : connectionUris()) {
      connections.add(connectionFactory.open(connectionUri, this::onMessage));
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

  /**
   * Reads one page of closed candles starting at {@code from}, at most {@link
   * #maxCandlesPerHistoryPage()} of them.
   *
   * @throws org.springframework.web.client.RestClientException if the request fails
   * @throws IllegalArgumentException if the answer is unreadable
   */
  public CandleList historyPage(
      String ticker, CandleInterval historyInterval, Instant from, Instant to) {
    String body =
        restClient
            .get()
            .uri(protocol.candlesUri(ticker, historyInterval, from, to, maxCandlesPerHistoryPage()))
            .retrieve()
            .body(String.class);
    return protocol.parseCandles(body, ticker, historyInterval);
  }

  public int maxCandlesPerHistoryPage() {
    return protocol.maxCandlesPerRestHistoryRequest();
  }

  List<URI> connectionUris() {
    int tickersPerConnection = tickersPerConnection();
    int tickerCount = tickers.size();
    return IntStream.iterate(0, start -> start < tickerCount, start -> start + tickersPerConnection)
        .mapToObj(start -> tickerGroupStartingAt(start, tickersPerConnection))
        .map(tickerGroup -> protocol.connectionUri(streamNamesFor(tickerGroup)))
        .toList();
  }

  private int tickersPerConnection() {
    int streamsPerTicker = protocol.streamNames(tickers.getFirst(), interval).streamNames().size();
    return Math.max(
        MIN_TICKERS_PER_CONNECTION, protocol.maxStreamsPerConnection() / streamsPerTicker);
  }

  private List<String> tickerGroupStartingAt(int start, int tickersPerConnection) {
    int end = Math.min(start + tickersPerConnection, tickers.size());
    return tickers.subList(start, end);
  }

  private List<String> streamNamesFor(List<String> tickerGroup) {
    return tickerGroup.stream()
        .flatMap(ticker -> protocol.streamNames(ticker, interval).streamNames().stream())
        .toList();
  }

  void onMessage(String message, Instant receivedAt) {
    Optional<MarketDataEvent> event;
    try {
      event = protocol.parse(message, receivedAt.plus(exchangeClockOffset));
    } catch (RuntimeException e) {
      log.warn("{}: unreadable message: {}", name, message, e);
      return;
    }
    event.ifPresent(this::applyMarketDataEvent);
  }

  private void applyMarketDataEvent(MarketDataEvent event) {
    switch (event) {
      case CandleUpdate candle -> applyCandleUpdate(candle);
      case BidAskUpdate bidAsk -> applyBidAskUpdate(bidAsk);
    }
  }

  private void applyCandleUpdate(CandleUpdate candle) {
    priceWriter.updateLastPrice(
        candle.ticker(), candle.close(), candle.eventTime(), TickerQuoteSource.WEBSOCKET);
    if (!queue.offer(candle)) {
      log.warn("{}: dispatch queue is full, dropped a candle update for {}", name, candle.ticker());
    }
  }

  private void applyBidAskUpdate(BidAskUpdate bidAsk) {
    priceWriter.updateBidAsk(
        bidAsk.ticker(),
        bidAsk.bid(),
        bidAsk.ask(),
        bidAsk.eventTime(),
        TickerQuoteSource.WEBSOCKET);
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
