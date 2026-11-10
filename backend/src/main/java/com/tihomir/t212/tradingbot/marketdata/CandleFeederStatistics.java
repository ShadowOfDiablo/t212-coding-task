package com.tihomir.t212.tradingbot.marketdata;

public record CandleFeederStatistics(
    long messages,
    long parseErrors,
    long droppedUpdates,
    int queueDepth,
    long averageParseMicros,
    long maxParseMicros,
    long averageLagMillis,
    long maxLagMillis,
    long exchangeClockOffsetMillis,
    int connections,
    int openConnections) {}
