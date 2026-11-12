package com.tihomir.t212.tradingbot.algorithm;

import java.util.List;

public record TradingAlgorithmList(List<TradingAlgorithm> algorithms) {

  public TradingAlgorithmList {
    algorithms = List.copyOf(algorithms);
  }
}
