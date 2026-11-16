package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.bot.run.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;

public record PricePointResponse(Instant time, BigDecimal price) {

  static PricePointResponse of(PricePoint point) {
    return new PricePointResponse(point.time(), point.price());
  }
}
