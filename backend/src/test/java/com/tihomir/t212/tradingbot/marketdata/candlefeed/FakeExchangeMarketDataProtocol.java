package com.tihomir.t212.tradingbot.marketdata.candlefeed;

import com.tihomir.t212.tradingbot.marketdata.update.BidAskUpdate;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.marketdata.update.MarketDataEvent;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Text protocol for tests: "C|ticker|close|eventMillis" is a candle, "B|ticker|bid|ask" a bid/ask
 * update, "ack" carries no data, anything else is malformed.
 */
public final class FakeExchangeMarketDataProtocol implements ExchangeMarketDataProtocol {

  static final int MAX_STREAMS = 1024;
  static final String STREAM_URI_PREFIX = "ws://stream.test/?streams=";
  static final String STREAM_SEPARATOR = "/";
  static final String SERVER_TIME_URI = "http://rest.test/time";
  static final int MAX_CANDLES_PER_REST_HISTORY_REQUEST = 2;

  @Override
  public StreamNameList streamNames(String ticker, CandleInterval interval) {
    String name = ticker.toLowerCase(Locale.ROOT);
    return new StreamNameList(List.of(name + "@candle", name + "@book"));
  }

  @Override
  public int maxStreamsPerConnection() {
    return MAX_STREAMS;
  }

  @Override
  public URI connectionUri(List<String> streams) {
    return URI.create(STREAM_URI_PREFIX + String.join(STREAM_SEPARATOR, streams));
  }

  @Override
  public Optional<MarketDataEvent> parse(String message, Instant exchangeReceivedAt) {
    String[] parts = message.split("\\|");
    return switch (parts[0]) {
      case "C" -> Optional.of(liveCandle(parts));
      case "B" -> Optional.of(bidAsk(parts, exchangeReceivedAt));
      case "ack" -> Optional.empty();
      default -> throw new IllegalArgumentException("bad message");
    };
  }

  @Override
  public int maxCandlesPerRestHistoryRequest() {
    return MAX_CANDLES_PER_REST_HISTORY_REQUEST;
  }

  @Override
  public URI candlesUri(
      String ticker, CandleInterval interval, Instant from, Instant to, int maxCandles) {
    return URI.create("http://rest.test/candles?from=" + from.getEpochSecond());
  }

  @Override
  public List<CandleUpdate> parseCandles(String body, String ticker, CandleInterval interval) {
    List<CandleUpdate> candles = new ArrayList<>();
    for (String second : body.split(",")) {
      long open = Long.parseLong(second);
      candles.add(candle(ticker, open, BigDecimal.ONE, (open + 1) * 1000, true));
    }
    return candles;
  }

  @Override
  public URI serverTimeUri() {
    return URI.create(SERVER_TIME_URI);
  }

  @Override
  public Instant parseServerTime(String body) {
    return Instant.ofEpochMilli(Long.parseLong(body));
  }

  private static CandleUpdate liveCandle(String[] parts) {
    return candle(parts[1], 0, new BigDecimal(parts[2]), Long.parseLong(parts[3]), false);
  }

  private static BidAskUpdate bidAsk(String[] parts, Instant eventTime) {
    return new BidAskUpdate(
        parts[1], new BigDecimal(parts[2]), new BigDecimal(parts[3]), eventTime);
  }

  private static CandleUpdate candle(
      String ticker, long openSecond, BigDecimal close, long eventMillis, boolean closed) {
    return new CandleUpdate(
        ticker,
        CandleInterval.ONE_SECOND,
        Instant.ofEpochSecond(openSecond),
        Instant.ofEpochSecond(openSecond + 1),
        close,
        close,
        close,
        close,
        BigDecimal.ZERO,
        closed,
        Instant.ofEpochMilli(eventMillis));
  }
}
