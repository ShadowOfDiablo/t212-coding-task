package com.tihomir.t212.tradingbot.position;

import java.util.Optional;

public interface PositionStore {

  /**
   * Writes the position, replaces its open lots and adds the new trades, in one transaction. A
   * position already stored is updated.
   */
  void savePosition(PositionWrite write);

  /** The open position the live bot left on this ticker, if any. Training positions don't count. */
  Optional<OpenPosition> findOpenLivePosition(String broker, String ticker);

  TradeList getTrades(long runId);

  StoredPositionList getWorstPositions(long runId, int maxPositions);
}
