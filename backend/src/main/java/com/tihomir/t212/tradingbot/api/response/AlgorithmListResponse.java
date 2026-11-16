package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithmList;
import java.util.List;

public record AlgorithmListResponse(List<AlgorithmResponse> algorithms) {

  public static AlgorithmListResponse of(TradingAlgorithmList algorithms) {
    return new AlgorithmListResponse(
        algorithms.algorithms().stream().map(AlgorithmResponse::of).toList());
  }
}
