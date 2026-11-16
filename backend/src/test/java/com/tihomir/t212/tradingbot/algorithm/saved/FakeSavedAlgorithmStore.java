package com.tihomir.t212.tradingbot.algorithm.saved;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmStore;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Keeps saved entries in a list, reading the settings of each variant from the algorithm store. */
public final class FakeSavedAlgorithmStore implements SavedAlgorithmStore {

  private final AlgorithmStore algorithmStore;
  private final List<SavedAlgorithm> entries = new ArrayList<>();
  private long nextId = 1;

  public FakeSavedAlgorithmStore(AlgorithmStore algorithmStore) {
    this.algorithmStore = algorithmStore;
  }

  @Override
  public long saveAlgorithm(long algorithmId, String name, Instant savedAt) {
    Optional<SavedAlgorithm> existing =
        entries.stream().filter(entry -> entry.algorithmId() == algorithmId).findFirst();
    if (existing.isPresent()) {
      SavedAlgorithm old = existing.get();
      entries.set(entries.indexOf(old), withName(old, name));
      return old.id();
    }
    AlgorithmSettings settings = algorithmStore.getAlgorithm(algorithmId).orElseThrow();
    int number = entries.stream().mapToInt(SavedAlgorithm::number).max().orElse(0) + 1;
    entries.add(new SavedAlgorithm(nextId, number, name, savedAt, algorithmId, settings));
    return nextId++;
  }

  @Override
  public boolean replaceAlgorithm(long savedAlgorithmId, long algorithmId) {
    Optional<SavedAlgorithm> entry = getSavedAlgorithm(savedAlgorithmId);
    if (entry.isEmpty()) {
      return false;
    }
    SavedAlgorithm old = entry.get();
    AlgorithmSettings settings = algorithmStore.getAlgorithm(algorithmId).orElseThrow();
    entries.set(
        entries.indexOf(old),
        new SavedAlgorithm(
            old.id(), old.number(), old.name(), old.savedAt(), algorithmId, settings));
    return true;
  }

  @Override
  public SavedAlgorithmList getSavedAlgorithms() {
    return new SavedAlgorithmList(entries);
  }

  @Override
  public Optional<SavedAlgorithm> getSavedAlgorithm(long savedAlgorithmId) {
    return entries.stream().filter(entry -> entry.id() == savedAlgorithmId).findFirst();
  }

  @Override
  public boolean deleteSavedAlgorithm(long savedAlgorithmId) {
    return entries.removeIf(entry -> entry.id() == savedAlgorithmId);
  }

  private static SavedAlgorithm withName(SavedAlgorithm entry, String name) {
    return new SavedAlgorithm(
        entry.id(), entry.number(), name, entry.savedAt(), entry.algorithmId(), entry.settings());
  }
}
