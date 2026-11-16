package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.SavedAlgorithmList;
import java.util.List;

/** Saved settings in the order they were first saved. */
public record SavedAlgorithmListResponse(List<SavedAlgorithmResponse> savedAlgorithms) {

  static SavedAlgorithmListResponse of(SavedAlgorithmList list) {
    return new SavedAlgorithmListResponse(
        list.savedAlgorithms().stream().map(SavedAlgorithmResponse::of).toList());
  }
}
