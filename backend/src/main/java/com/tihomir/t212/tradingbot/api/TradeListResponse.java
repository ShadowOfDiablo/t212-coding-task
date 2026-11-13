package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.position.TradeList;
import java.util.List;

public record TradeListResponse(List<TradeResponse> trades) {

  static TradeListResponse of(TradeList trades) {
    return new TradeListResponse(trades.trades().stream().map(TradeResponse::of).toList());
  }
}
