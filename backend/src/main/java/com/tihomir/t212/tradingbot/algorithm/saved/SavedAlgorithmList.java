package com.tihomir.t212.tradingbot.algorithm.saved;

import java.util.List;

/** Saved settings in the order they were first saved. */
public record SavedAlgorithmList(List<SavedAlgorithm> savedAlgorithms) {

  public SavedAlgorithmList {
    savedAlgorithms = List.copyOf(savedAlgorithms);
  }
}
