package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithmList;
import java.util.List;

/** Saved settings in the order they were first saved. */
public record SavedAlgorithmListResponse(List<SavedAlgorithmResponse> savedAlgorithms) {

  public static SavedAlgorithmListResponse of(SavedAlgorithmList list) {
    return new SavedAlgorithmListResponse(
        list.savedAlgorithms().stream().map(SavedAlgorithmResponse::of).toList());
  }
}
