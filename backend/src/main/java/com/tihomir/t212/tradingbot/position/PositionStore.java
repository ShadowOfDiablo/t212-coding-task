package com.tihomir.t212.tradingbot.position;

import java.time.Instant;

public interface PositionStore {

  /** Writes the position. A position already stored is updated. */
  void savePosition(PositionWrite write);

  StoredPositionList getWorstPositions(long runId, int maxPositions);

  /** Every position of the run, open ones included, the newest first. */
  StoredPositionList getPositions(long runId);

  /**
   * Closes every open position of the broker's runs at {@code closedAt}, keeping their realised
   * P&L; positions keep no quantity, so a later sale can't be split between them.
   *
   * @return how many positions were closed
   */
  int closeOpenPositions(String broker, Instant closedAt);
}
