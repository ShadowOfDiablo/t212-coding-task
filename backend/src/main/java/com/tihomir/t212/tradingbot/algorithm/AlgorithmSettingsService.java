package com.tihomir.t212.tradingbot.algorithm;

import java.time.Clock;
import org.springframework.stereotype.Service;

/** Saves and reads algorithm variants. */
@Service
public final class AlgorithmSettingsService {

  private final AlgorithmStore store;
  private final Clock clock;

  public AlgorithmSettingsService(AlgorithmStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  /** The id of the stored variant, reused when the same settings were saved before. */
  public long saveSettings(AlgorithmSettings settings) {
    return store.findOrInsertAlgorithm(settings, clock.instant());
  }

  /**
   * @throws AlgorithmSettingsNotFoundException if no variant has this id
   */
  public AlgorithmSettings getSettings(long algorithmId) {
    return store
        .getAlgorithm(algorithmId)
        .orElseThrow(() -> new AlgorithmSettingsNotFoundException(algorithmId));
  }
}
