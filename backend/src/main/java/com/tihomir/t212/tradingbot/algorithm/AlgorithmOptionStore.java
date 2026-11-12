package com.tihomir.t212.tradingbot.algorithm;

import java.util.Optional;

/**
 * Stores the settings of one algorithm type: the shared {@code algorithm_settings} row plus the
 * algorithm's own table, whose key points back to that row. Identical settings are stored once.
 */
public interface AlgorithmOptionStore {

  int algorithmId();

  /** The id of a stored row with exactly these settings, inserting it if there is none. */
  long findOrInsertSettings(AlgorithmSettings settings);

  /** Empty if no settings of this algorithm have this id. */
  Optional<AlgorithmSettings> getSettings(long settingsId);
}
