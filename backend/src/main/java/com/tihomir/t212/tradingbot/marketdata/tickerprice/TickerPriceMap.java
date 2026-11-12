package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class TickerPriceMap implements TickerPriceReader, TickerPriceWriter {

  private final List<String> tickerOrder = new CopyOnWriteArrayList<>();
  private final Map<String, AtomicReference<TickerQuote>> quotes = new ConcurrentHashMap<>();

  public TickerPriceMap(Collection<String> tickers) {
    tickers.forEach(this::addTicker);
  }

  @Override
  public TickerList tickers() {
    return new TickerList(tickerOrder);
  }

  /** A followed ticker starts empty; adding one that is already there changes nothing. */
  public synchronized void addTicker(String ticker) {
    if (quotes.putIfAbsent(ticker, new AtomicReference<>()) == null) {
      tickerOrder.add(ticker);
    }
  }

  @Override
  public Optional<TickerQuote> get(String ticker) {
    AtomicReference<TickerQuote> quote = quotes.get(ticker);
    if (quote == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(quote.get());
  }

  @Override
  public void updateLastPrice(
      String ticker, BigDecimal lastPrice, Instant time, TickerQuoteSource source) {
    AtomicReference<TickerQuote> quote = quotes.get(ticker);
    if (quote != null) {
      quote.updateAndGet(previous -> withLastPrice(previous, lastPrice, time, source));
    }
  }

  @Override
  public void updateBidAsk(
      String ticker, BigDecimal bid, BigDecimal ask, Instant time, TickerQuoteSource source) {
    AtomicReference<TickerQuote> quote = quotes.get(ticker);
    if (quote != null) {
      quote.updateAndGet(previous -> withBidAsk(previous, bid, ask, time, source));
    }
  }

  private static TickerQuote withLastPrice(
      TickerQuote previous, BigDecimal lastPrice, Instant time, TickerQuoteSource source) {
    if (previous == null) {
      return new TickerQuote(null, null, lastPrice, time, source);
    }
    return previous.withLastPrice(lastPrice, time, source);
  }

  private static TickerQuote withBidAsk(
      TickerQuote previous,
      BigDecimal bid,
      BigDecimal ask,
      Instant time,
      TickerQuoteSource source) {
    if (previous == null) {
      return new TickerQuote(bid, ask, null, time, source);
    }
    return previous.withBidAsk(bid, ask, time, source);
  }
}
