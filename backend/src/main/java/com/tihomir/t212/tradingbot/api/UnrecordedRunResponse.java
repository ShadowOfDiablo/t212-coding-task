package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.UnrecordedRun;
import java.math.BigDecimal;
import java.util.List;

public record UnrecordedRunResponse(
    BigDecimal finalValue,
    int buyCount,
    int sellCount,
    int stopLossCount,
    int takeProfitCount,
    int refusedOrderCount,
    List<PortfolioPointResponse> points) {

  static UnrecordedRunResponse of(UnrecordedRun run) {
    return new UnrecordedRunResponse(
        run.finalValue(),
        run.counts().buyCount(),
        run.counts().sellCount(),
        run.counts().stopLossCount(),
        run.counts().takeProfitCount(),
        run.counts().refusedOrderCount(),
        run.points().points().stream().map(PortfolioPointResponse::of).toList());
  }
}
