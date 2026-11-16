package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.SavedAlgorithm;
import java.time.Instant;

public record SavedAlgorithmResponse(
    long id,
    int number,
    String name,
    Instant savedAt,
    long algorithmId,
    AlgorithmSettingsResponse settings) {

  static SavedAlgorithmResponse of(SavedAlgorithm saved) {
    return new SavedAlgorithmResponse(
        saved.id(),
        saved.number(),
        saved.name(),
        saved.savedAt(),
        saved.algorithmId(),
        AlgorithmSettingsResponse.of(saved.settings()));
  }
}
