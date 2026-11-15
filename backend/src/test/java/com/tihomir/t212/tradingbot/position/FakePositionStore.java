package com.tihomir.t212.tradingbot.position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataAccessResourceFailureException;

/** Keeps every position write in memory; it can be told to fail like a database that is down. */
public final class FakePositionStore implements PositionStore {

  private final List<PositionWrite> writes = new ArrayList<>();
  private boolean failing;

  @Override
  public synchronized void savePosition(PositionWrite write) {
    if (failing) {
      throw new DataAccessResourceFailureException("connection refused");
    }
    writes.add(write);
  }

  @Override
  public synchronized StoredPositionList getWorstPositions(long runId, int maxPositions) {
    return new StoredPositionList(
        writes.stream()
            .map(PositionWrite::position)
            .filter(position -> position.runId() == runId)
            .filter(position -> position.status() == PositionStatus.CLOSED)
            .sorted(Comparator.comparing(Position::realisedPnl))
            .limit(maxPositions)
            .map(position -> new StoredPosition(writes.size(), position))
            .toList());
  }

  @Override
  public synchronized StoredPositionList getPositions(long runId) {
    return new StoredPositionList(
        writes.stream()
            .map(PositionWrite::position)
            .filter(position -> position.runId() == runId)
            .sorted(Comparator.comparing(Position::openedAt).reversed())
            .map(position -> new StoredPosition(writes.size(), position))
            .toList());
  }

  public synchronized void failWrites() {
    failing = true;
  }

  public synchronized PositionWriteList writes() {
    return new PositionWriteList(writes);
  }
}
