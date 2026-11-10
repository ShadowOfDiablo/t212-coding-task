package com.tihomir.t212.tradingbot.marketdata;

import java.math.BigDecimal;

public record BestBidAskUpdate(String ticker, BigDecimal bid, BigDecimal ask)
    implements MarketDataEvent {}
