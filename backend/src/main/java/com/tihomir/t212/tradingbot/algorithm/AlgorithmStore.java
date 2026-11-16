package com.tihomir.t212.tradingbot.algorithm;

import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import java.time.Instant;
import java.util.Optional;

/** Algorithm variants: a type with its settings. Identical variants are stored once. */
public interface AlgorithmStore {

  /** The id of the stored variant with exactly these settings, inserting it if there is none. */
  long findOrInsertAlgorithm(AlgorithmSettings settings, Instant createdAt);

  Optional<AlgorithmSettings> getAlgorithm(long algorithmId);
}
