package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import org.springframework.stereotype.Service;

/** Reads recorded runs, live and training. */
@Service
public class AlgorithmRunService {

  private final AlgorithmRunStore runStore;
  private final AlgorithmSettingsService settingsService;

  public AlgorithmRunService(AlgorithmRunStore runStore, AlgorithmSettingsService settingsService) {
    this.runStore = runStore;
    this.settingsService = settingsService;
  }

  public RecordedRunList getRuns(String broker) {
    return runStore.getRuns(broker);
  }

  /**
   * @throws RunNotFoundException if the broker has no run with this id
   */
  public RunDetails getRun(String broker, long runId) {
    StoredRunDetails stored =
        runStore
            .getRun(runId)
            .filter(details -> details.run().broker().equals(broker))
            .orElseThrow(() -> new RunNotFoundException(runId));
    return new RunDetails(
        stored.run(),
        new RunSettingsHistory(
            stored.settingsChanges().changes().stream().map(this::settingsRecord).toList()),
        stored.points());
  }

  private RunSettingsRecord settingsRecord(RunSettingsChange change) {
    return new RunSettingsRecord(
        change.event(),
        change.recordedAt(),
        change.settingsId(),
        settingsService.getSettings(change.settingsId()));
  }
}
