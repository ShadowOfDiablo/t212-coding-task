package com.tihomir.t212.tradingbot.position;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

  @Override
  public synchronized int closeOpenPositions(String broker, Instant closedAt) {
    List<PositionWrite> open =
        writes.stream()
            .filter(write -> write.position().broker().equals(broker))
            .filter(write -> write.position().status() == PositionStatus.OPEN)
            .filter(write -> isLatestWriteOf(write.position()))
            .toList();
    open.forEach(write -> writes.add(closedWrite(write, closedAt)));
    return open.size();
  }

  private boolean isLatestWriteOf(Position position) {
    return writes.stream()
        .map(PositionWrite::position)
        .filter(other -> other.runId() == position.runId())
        .filter(other -> other.openedAt().equals(position.openedAt()))
        .reduce((first, second) -> second)
        .orElseThrow()
        .equals(position);
  }

  private static PositionWrite closedWrite(PositionWrite write, Instant closedAt) {
    Position position = write.position();
    return new PositionWrite(
        new Position(
            position.runId(),
            position.broker(),
            position.ticker(),
            PositionStatus.CLOSED,
            position.openedAt(),
            Optional.of(closedAt),
            position.cost(),
            position.realisedPnl(),
            position.mae(),
            position.mfe(),
            position.prices()));
  }

  public synchronized void failWrites() {
    failing = true;
  }

  public synchronized PositionWriteList writes() {
    return new PositionWriteList(writes);
  }
}
