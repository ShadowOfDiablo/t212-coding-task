package com.tihomir.t212.tradingbot.marketdata;

public record CandleFeederStatistics(
    long messages,
    long parseErrors,
    long droppedUpdates,
    int queueDepth,
    long avgParseMicros,
    long maxParseMicros,
    long avgLagMillis,
    long maxLagMillis,
    int connections,
    int openConnections) {}
