package com.tihomir.t212.tradingbot.bot;

import java.time.Instant;
import java.util.Optional;

public interface AlgorithmRunStore {

  /**
   * Inserts the run as STARTED. A live run also becomes the active algorithm of its ticker, in the
   * same transaction.
   */
  long insertStartedRun(RunStart start);

  /**
   * Stores how the run ended: status, counts, final value, points and position writes, in one
   * transaction.
   */
  void finishRun(long runId, RunEnd end);

  /**
   * Finishes a live run and starts the next one with another algorithm variant, in one transaction.
   *
   * @return the id of the new run
   */
  long replaceRun(long runId, RunEnd end, RunStart next);

  /**
   * Marks runs still STARTED as STOPPED with this reason. Used at startup, when no run can still be
   * going.
   *
   * @return how many runs were stopped
   */
  int stopUnfinishedRuns(Instant stoppedAt, String reason);

  Optional<StoredRunDetails> getRun(long runId);

  /** The latest runs of a broker, newest first, without points. */
  RecordedRunList getRuns(String broker);
}
