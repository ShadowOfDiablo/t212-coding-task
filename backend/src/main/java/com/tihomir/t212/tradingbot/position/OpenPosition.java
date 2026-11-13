package com.tihomir.t212.tradingbot.position;

/** An open position with its lots, as stored. */
public record OpenPosition(Position position, OpenLotList openLots) {}
