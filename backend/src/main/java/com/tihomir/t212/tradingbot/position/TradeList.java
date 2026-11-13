package com.tihomir.t212.tradingbot.position;

import java.util.List;

public record TradeList(List<Trade> trades) {

  public static final TradeList NONE = new TradeList(List.of());

  public TradeList {
    trades = List.copyOf(trades);
  }
}
