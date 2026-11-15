package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.position.PositionStore;
import com.tihomir.t212.tradingbot.position.StoredPositionList;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmRunService {

  private final AlgorithmRunStore runStore;
  private final AlgorithmSettingsService settingsService;
  private final PositionStore positionStore;

  public AlgorithmRunService(
      AlgorithmRunStore runStore,
      AlgorithmSettingsService settingsService,
      PositionStore positionStore) {
    this.runStore = runStore;
    this.settingsService = settingsService;
    this.positionStore = positionStore;
  }

  public RecordedRunList getRuns(String broker) {
    return runStore.getRuns(broker);
  }

  /**
   * @throws RunNotFoundException if the broker has no run with this id
   */
  public RunDetails getRun(String broker, long runId) {
    StoredRunDetails stored = storedRun(broker, runId);
    return new RunDetails(
        stored.run(), settingsService.getSettings(stored.run().algorithmId()), stored.points());
  }

  /**
   * @throws RunNotFoundException if the broker has no run with this id
   */
  public StoredPositionList getWorstPositions(String broker, long runId, int maxPositions) {
    storedRun(broker, runId);
    return positionStore.getWorstPositions(runId, maxPositions);
  }

  /**
   * @throws RunNotFoundException if the broker has no run with this id
   */
  public StoredPositionList getPositions(String broker, long runId) {
    storedRun(broker, runId);
    return positionStore.getPositions(runId);
  }

  private StoredRunDetails storedRun(String broker, long runId) {
    return runStore
        .getRun(runId)
        .filter(details -> details.run().broker().equals(broker))
        .orElseThrow(() -> new RunNotFoundException(runId));
  }
}
