package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.marketdata.BestBidAskUpdate;
import com.tihomir.t212.tradingbot.marketdata.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.CandleUpdate;
import com.tihomir.t212.tradingbot.marketdata.ExchangeMarketDataProtocol;
import com.tihomir.t212.tradingbot.marketdata.MarketDataEvent;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class BinanceMarketDataProtocol implements ExchangeMarketDataProtocol {

  private static final int MAX_STREAMS_PER_CONNECTION = 1024;
  private static final int MAX_CANDLES_PER_REST_HISTORY_REQUEST = 1000;
  private static final String CANDLE_STREAM = "@kline_";
  private static final String BOOK_TICKER_STREAM = "@bookTicker";

  private final URI streamUrl;
  private final URI restUrl;
  private final JsonMapper jsonMapper;

  BinanceMarketDataProtocol(URI streamUrl, URI restUrl, JsonMapper jsonMapper) {
    this.streamUrl = streamUrl;
    this.restUrl = restUrl;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public List<String> streamNames(String ticker, CandleInterval interval) {
    String symbol = ticker.toLowerCase(Locale.ROOT);
    return List.of(symbol + CANDLE_STREAM + intervalCode(interval), symbol + BOOK_TICKER_STREAM);
  }

  @Override
  public int maxStreamsPerConnection() {
    return MAX_STREAMS_PER_CONNECTION;
  }

  @Override
  public URI connectionUri(List<String> streams) {
    return URI.create(streamUrl + "/stream?streams=" + String.join("/", streams));
  }

  @Override
  public Optional<MarketDataEvent> parse(String message) {
    JsonNode root = jsonMapper.readTree(message);
    JsonNode stream = root.get("stream");
    JsonNode data = root.get("data");
    if (stream == null || data == null) {
      return Optional.empty();
    }
    String name = stream.asString();
    if (name.endsWith(BOOK_TICKER_STREAM)) {
      return Optional.of(
          new BestBidAskUpdate(
              required(data, "s").asString(), decimal(data, "b"), decimal(data, "a")));
    }
    if (name.contains(CANDLE_STREAM)) {
      JsonNode candle = required(data, "k");
      return Optional.of(
          new CandleUpdate(
              required(candle, "s").asString(),
              intervalFromCode(required(candle, "i").asString()),
              instant(candle, "t"),
              instant(candle, "T"),
              decimal(candle, "o"),
              decimal(candle, "h"),
              decimal(candle, "l"),
              decimal(candle, "c"),
              decimal(candle, "v"),
              required(candle, "x").asBoolean(),
              instant(data, "E")));
    }
    return Optional.empty();
  }

  @Override
  public int maxCandlesPerRestHistoryRequest() {
    return MAX_CANDLES_PER_REST_HISTORY_REQUEST;
  }

  @Override
  public URI candlesUri(
      String ticker, CandleInterval interval, Instant from, Instant to, int maxCandles) {
    return UriComponentsBuilder.fromUri(restUrl)
        .path("/api/v3/klines")
        .queryParam("symbol", ticker)
        .queryParam("interval", intervalCode(interval))
        .queryParam("startTime", from.toEpochMilli())
        .queryParam("endTime", to.toEpochMilli() - 1)
        .queryParam("limit", maxCandles)
        .build()
        .toUri();
  }

  @Override
  public List<CandleUpdate> parseCandles(String body, String ticker, CandleInterval interval) {
    List<CandleUpdate> candles = new ArrayList<>();
    for (JsonNode row : jsonMapper.readTree(body)) {
      Instant closeTime = Instant.ofEpochMilli(row.get(6).asLong());
      candles.add(
          new CandleUpdate(
              ticker,
              interval,
              Instant.ofEpochMilli(row.get(0).asLong()),
              closeTime,
              new BigDecimal(row.get(1).asString()),
              new BigDecimal(row.get(2).asString()),
              new BigDecimal(row.get(3).asString()),
              new BigDecimal(row.get(4).asString()),
              new BigDecimal(row.get(5).asString()),
              true,
              closeTime));
    }
    return candles;
  }

  static String intervalCode(CandleInterval interval) {
    return switch (interval) {
      case ONE_SECOND -> "1s";
      case ONE_MINUTE -> "1m";
      case FIVE_MINUTES -> "5m";
      case FIFTEEN_MINUTES -> "15m";
      case ONE_HOUR -> "1h";
      case FOUR_HOURS -> "4h";
      case ONE_DAY -> "1d";
    };
  }

  static CandleInterval intervalFromCode(String code) {
    for (CandleInterval interval : CandleInterval.values()) {
      if (intervalCode(interval).equals(code)) {
        return interval;
      }
    }
    throw new IllegalArgumentException("Unknown Binance interval: " + code);
  }

  private static JsonNode required(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException("Missing field: " + field);
    }
    return value;
  }

  private static BigDecimal decimal(JsonNode node, String field) {
    return new BigDecimal(required(node, field).asString());
  }

  private static Instant instant(JsonNode node, String field) {
    return Instant.ofEpochMilli(required(node, field).asLong());
  }
}
