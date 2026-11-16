package com.tihomir.t212.tradingbot.algorithm.saved;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.exception.InvalidAlgorithmSettingsException;
import com.tihomir.t212.tradingbot.algorithm.exception.SavedAlgorithmNotFoundException;
import com.tihomir.t212.tradingbot.algorithm.exception.SettingsAlreadySavedException;
import com.tihomir.t212.tradingbot.algorithm.exception.UnknownAlgorithmException;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public final class SavedAlgorithmService {

  private final AlgorithmRegistryService algorithms;
  private final AlgorithmSettingsService settingsService;
  private final SavedAlgorithmStore store;
  private final Clock clock;

  public SavedAlgorithmService(
      AlgorithmRegistryService algorithms,
      AlgorithmSettingsService settingsService,
      SavedAlgorithmStore store,
      Clock clock) {
    this.algorithms = algorithms;
    this.settingsService = settingsService;
    this.store = store;
    this.clock = clock;
  }

  /**
   * Checks the settings, finds or inserts their variant, then saves it under the name. Saving the
   * same settings again renames their entry.
   *
   * @throws UnknownAlgorithmException for an algorithm type that isn't registered
   * @throws InvalidAlgorithmSettingsException for settings the algorithm doesn't allow
   */
  public SavedAlgorithm saveAlgorithm(String name, AlgorithmSettingsRequest request) {
    long algorithmId = settingsService.saveSettings(resolve(request));
    long savedAlgorithmId = store.saveAlgorithm(algorithmId, name.strip(), clock.instant());
    return getSavedAlgorithm(savedAlgorithmId);
  }

  /**
   * Puts new settings under an existing entry, keeping its name and place in the list.
   *
   * @throws SavedAlgorithmNotFoundException if there is no entry with this id
   * @throws SettingsAlreadySavedException if another entry already holds these settings
   * @throws InvalidAlgorithmSettingsException for settings the algorithm doesn't allow
   */
  public SavedAlgorithm replaceSettings(long savedAlgorithmId, AlgorithmSettingsRequest request) {
    SavedAlgorithm entry = getSavedAlgorithm(savedAlgorithmId);
    long algorithmId = settingsService.saveSettings(resolve(request));
    Optional<SavedAlgorithm> otherEntry =
        store.getSavedAlgorithms().savedAlgorithms().stream()
            .filter(other -> other.algorithmId() == algorithmId && other.id() != entry.id())
            .findFirst();
    if (otherEntry.isPresent()) {
      throw new SettingsAlreadySavedException(otherEntry.get().name());
    }
    store.replaceAlgorithm(entry.id(), algorithmId);
    return getSavedAlgorithm(entry.id());
  }

  public SavedAlgorithmList getSavedAlgorithms() {
    return store.getSavedAlgorithms();
  }

  /**
   * @throws SavedAlgorithmNotFoundException if there is no entry with this id
   */
  public void deleteSavedAlgorithm(long savedAlgorithmId) {
    if (!store.deleteSavedAlgorithm(savedAlgorithmId)) {
      throw new SavedAlgorithmNotFoundException(savedAlgorithmId);
    }
  }

  private AlgorithmSettings resolve(AlgorithmSettingsRequest request) {
    return algorithms.get(request.algorithmType()).resolveSettings(request);
  }

  private SavedAlgorithm getSavedAlgorithm(long savedAlgorithmId) {
    return store
        .getSavedAlgorithm(savedAlgorithmId)
        .orElseThrow(() -> new SavedAlgorithmNotFoundException(savedAlgorithmId));
  }
}
