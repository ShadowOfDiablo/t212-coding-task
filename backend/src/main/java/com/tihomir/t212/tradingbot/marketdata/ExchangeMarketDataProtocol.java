package com.tihomir.t212.tradingbot.marketdata;

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
   * @throws IllegalArgumentException if the message is malformed
   */
  Optional<MarketDataEvent> parse(String message);

  int maxCandlesPerRestHistoryRequest();

  URI candlesUri(String ticker, CandleInterval interval, Instant from, Instant to, int maxCandles);

  List<CandleUpdate> parseCandles(String body, String ticker, CandleInterval interval);
}
