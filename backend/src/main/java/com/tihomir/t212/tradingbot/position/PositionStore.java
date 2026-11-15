package com.tihomir.t212.tradingbot.position;

public interface PositionStore {

  /** Writes the position. A position already stored is updated. */
  void savePosition(PositionWrite write);

  StoredPositionList getWorstPositions(long runId, int maxPositions);
}
