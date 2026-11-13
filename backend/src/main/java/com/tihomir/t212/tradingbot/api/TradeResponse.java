package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.position.Trade;
import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(
    long runId,
    Instant boughtAt,
    Instant soldAt,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal fee,
    BigDecimal realisedPnl) {

  static TradeResponse of(Trade trade) {
    return new TradeResponse(
        trade.runId(),
        trade.boughtAt(),
        trade.soldAt(),
        trade.quantity(),
        trade.entryPrice(),
        trade.exitPrice(),
        trade.fee(),
        trade.realisedPnl());
  }
}
