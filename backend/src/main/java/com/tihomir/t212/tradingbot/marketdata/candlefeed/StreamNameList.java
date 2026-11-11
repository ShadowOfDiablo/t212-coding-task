package com.tihomir.t212.tradingbot.marketdata.candlefeed;

import java.util.List;

public record StreamNameList(List<String> streamNames) {

  public StreamNameList {
    streamNames = List.copyOf(streamNames);
  }
}
