package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import java.util.List;

/**
 * One algorithm. {@code timeframe} and {@code liveOnTick} are shared by every algorithm and are not
 * listed in {@code settings}, which holds the numeric settings.
 */
public record AlgorithmResponse(
    AlgorithmType type,
    String name,
    String displayName,
    String description,
    List<AlgorithmSettingDefinitionResponse> settings) {

  static AlgorithmResponse of(TradingAlgorithm algorithm) {
    return new AlgorithmResponse(
        algorithm.type(),
        algorithm.name(),
        algorithm.displayName(),
        algorithm.description(),
        algorithm.settingDefinitions().definitions().stream()
            .map(AlgorithmSettingDefinitionResponse::of)
            .toList());
  }
}
