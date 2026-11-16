package com.tihomir.t212.tradingbot.algorithm;

import java.time.Instant;
import java.util.Optional;

public interface SavedAlgorithmStore {

  /**
   * Saves the variant under the name and returns the entry's id. A variant saved before keeps its
   * entry and only gets the new name.
   */
  long saveAlgorithm(long algorithmId, String name, Instant savedAt);

  /**
   * Points the entry at another variant; its name and save time stay. False if there was no such
   * entry.
   */
  boolean replaceAlgorithm(long savedAlgorithmId, long algorithmId);

  SavedAlgorithmList getSavedAlgorithms();

  Optional<SavedAlgorithm> getSavedAlgorithm(long savedAlgorithmId);

  /** Removes the entry only; the variant stays. False if there was no such entry. */
  boolean deleteSavedAlgorithm(long savedAlgorithmId);
}
