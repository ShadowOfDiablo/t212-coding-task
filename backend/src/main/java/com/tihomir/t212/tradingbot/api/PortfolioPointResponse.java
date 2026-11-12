package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.PortfolioPoint;
import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioPointResponse(Instant time, BigDecimal cash, BigDecimal value) {

  static PortfolioPointResponse of(PortfolioPoint point) {
    return new PortfolioPointResponse(point.time(), point.cash(), point.value());
  }
}
