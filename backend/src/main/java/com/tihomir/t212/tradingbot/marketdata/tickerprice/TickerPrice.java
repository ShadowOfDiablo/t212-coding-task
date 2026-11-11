package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import java.util.Optional;

/** A traded ticker with its latest quote; the quote is empty until market data arrives. */
public record TickerPrice(String ticker, Optional<TickerQuote> quote) {}
