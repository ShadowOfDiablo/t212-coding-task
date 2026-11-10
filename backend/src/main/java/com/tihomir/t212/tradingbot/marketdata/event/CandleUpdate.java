package com.tihomir.t212.tradingbot.marketdata.event;

import java.math.BigDecimal;
import java.time.Instant;

public record CandleUpdate(
    String ticker,
    CandleInterval interval,
    Instant openTime,
    Instant closeTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume,
    boolean closed,
    Instant eventTime)
    implements MarketDataEvent {}
