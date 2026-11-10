package com.tihomir.t212.tradingbot.marketdata.event;

import java.math.BigDecimal;
import java.time.Instant;

public record BidAskUpdate(String ticker, BigDecimal bid, BigDecimal ask, Instant eventTime)
    implements MarketDataEvent {}
