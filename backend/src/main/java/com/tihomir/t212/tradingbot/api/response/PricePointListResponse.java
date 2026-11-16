package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.bot.run.PricePointList;
import java.util.List;

public record PricePointListResponse(List<PricePointResponse> points) {

  public static PricePointListResponse of(PricePointList points) {
    return new PricePointListResponse(
        points.points().stream().map(PricePointResponse::of).toList());
  }
}
