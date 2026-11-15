package com.tihomir.t212.tradingbot.position;

public interface PositionStore {

  /** Writes the position. A position already stored is updated. */
  void savePosition(PositionWrite write);

  StoredPositionList getWorstPositions(long runId, int maxPositions);

  /** Every position of the run, open ones included, the newest first. */
  StoredPositionList getPositions(long runId);
}
