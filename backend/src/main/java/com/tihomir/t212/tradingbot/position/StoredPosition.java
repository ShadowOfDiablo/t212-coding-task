package com.tihomir.t212.tradingbot.position;

/** A stored position with its database id. */
public record StoredPosition(long id, Position position) {}
