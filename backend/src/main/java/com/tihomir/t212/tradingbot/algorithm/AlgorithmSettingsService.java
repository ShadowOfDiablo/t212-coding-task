package com.tihomir.t212.tradingbot.algorithm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Saves and reads settings through the store of each algorithm type. */
@Service
public final class AlgorithmSettingsService {

  private final Map<Integer, AlgorithmOptionStore> storesByAlgorithm;

  public AlgorithmSettingsService(List<AlgorithmOptionStore> stores) {
    this.storesByAlgorithm =
        stores.stream()
            .collect(
                Collectors.toMap(
                    AlgorithmOptionStore::algorithmId,
                    Function.identity(),
                    AlgorithmSettingsService::refuseDuplicateStore));
  }

  /**
   * The id of the stored settings, reused when the same settings were saved before.
   *
   * @throws UnknownAlgorithmException if no store handles the settings' algorithm
   */
  public long saveSettings(AlgorithmSettings settings) {
    AlgorithmOptionStore store = storesByAlgorithm.get(settings.algorithmId());
    if (store == null) {
      throw new UnknownAlgorithmException(settings.algorithmId());
    }
    return store.findOrInsertSettings(settings);
  }

  /**
   * @throws AlgorithmSettingsNotFoundException if no stored settings have this id
   */
  public AlgorithmSettings getSettings(long settingsId) {
    return storesByAlgorithm.values().stream()
        .map(store -> store.getSettings(settingsId))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow(() -> new AlgorithmSettingsNotFoundException(settingsId));
  }

  private static AlgorithmOptionStore refuseDuplicateStore(
      AlgorithmOptionStore first, AlgorithmOptionStore next) {
    throw new IllegalStateException("Two settings stores handle " + first.algorithmId());
  }
}
