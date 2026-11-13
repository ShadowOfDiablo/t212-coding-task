package com.tihomir.t212.tradingbot.position;

/**
 * What one executed order changed: the position after it, all its lots that still hold coins, and
 * the trades the order added.
 */
public record PositionWrite(Position position, OpenLotList openLots, TradeList newTrades) {}
