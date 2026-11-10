package com.tihomir.t212.tradingbot.marketdata.feed;

import com.tihomir.t212.tradingbot.marketdata.event.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.event.CandleUpdate;
import com.tihomir.t212.tradingbot.marketdata.event.MarketDataEvent;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExchangeMarketDataProtocol {

  List<String> streamNames(String ticker, CandleInterval interval);

  int maxStreamsPerConnection();

  URI connectionUri(List<String> streams);

  /**
   * Returns empty for messages that carry no market data, such as subscription acknowledgements.
   *
   * @param exchangeReceivedAt receive time on the exchange clock, the event time of messages that
   *     carry none
   * @throws IllegalArgumentException if the message is malformed
   */
  Optional<MarketDataEvent> parse(String message, Instant exchangeReceivedAt);

  int maxCandlesPerRestHistoryRequest();

  URI candlesUri(String ticker, CandleInterval interval, Instant from, Instant to, int maxCandles);

  List<CandleUpdate> parseCandles(String body, String ticker, CandleInterval interval);

  URI serverTimeUri();

  /**
   * @throws IllegalArgumentException if the body is missing or malformed
   */
  Instant parseServerTime(String body);
}
