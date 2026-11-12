package com.tihomir.t212.tradingbot.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Keeps settings in a list; the index plus an offset is the id. */
public final class FakeAlgorithmOptionStore implements AlgorithmOptionStore {

  private final int algorithmId;
  private final long firstId;
  private final List<AlgorithmSettings> storedSettings = new ArrayList<>();

  public FakeAlgorithmOptionStore(int algorithmId, long firstId) {
    this.algorithmId = algorithmId;
    this.firstId = firstId;
  }

  @Override
  public int algorithmId() {
    return algorithmId;
  }

  @Override
  public long findOrInsertSettings(AlgorithmSettings settings) {
    int index = storedSettings.indexOf(settings);
    if (index < 0) {
      storedSettings.add(settings);
      index = storedSettings.size() - 1;
    }
    return firstId + index;
  }

  @Override
  public Optional<AlgorithmSettings> getSettings(long settingsId) {
    long index = settingsId - firstId;
    if (index < 0 || index >= storedSettings.size()) {
      return Optional.empty();
    }
    return Optional.of(storedSettings.get((int) index));
  }
}
