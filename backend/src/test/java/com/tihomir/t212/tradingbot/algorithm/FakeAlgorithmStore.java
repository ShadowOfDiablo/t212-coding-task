package com.tihomir.t212.tradingbot.algorithm;

import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Keeps variants in a list; the index plus an offset is the id. */
public final class FakeAlgorithmStore implements AlgorithmStore {

  private final long firstId;
  private final List<AlgorithmSettings> storedSettings = new ArrayList<>();

  public FakeAlgorithmStore(long firstId) {
    this.firstId = firstId;
  }

  @Override
  public long findOrInsertAlgorithm(AlgorithmSettings settings, Instant createdAt) {
    int index = storedSettings.indexOf(settings);
    if (index < 0) {
      storedSettings.add(settings);
      index = storedSettings.size() - 1;
    }
    return firstId + index;
  }

  @Override
  public Optional<AlgorithmSettings> getAlgorithm(long algorithmId) {
    long index = algorithmId - firstId;
    if (index < 0 || index >= storedSettings.size()) {
      return Optional.empty();
    }
    return Optional.of(storedSettings.get((int) index));
  }
}
