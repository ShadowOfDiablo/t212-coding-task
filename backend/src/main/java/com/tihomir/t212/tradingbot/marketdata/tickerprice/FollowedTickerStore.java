package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.time.Instant;

/** Tickers followed through the API, on top of the configured ones. */
public interface FollowedTickerStore {

  /** Stores the ticker; storing one that is already there changes nothing. */
  void insertFollowedTicker(String broker, String ticker, Instant followedAt);

  /** The followed tickers of the broker, oldest first. */
  TickerList getFollowedTickers(String broker);
}
