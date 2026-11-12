package com.tihomir.t212.tradingbot.bot;

import java.time.Instant;
import java.util.Optional;

public interface AlgorithmRunStore {

  /** Inserts the run as STARTED together with its STARTED settings record, in one transaction. */
  long insertStartedRun(RunStart start);

  /** Records settings saved while a live run goes on. */
  void insertSettingsChange(long runId, RunSettingsChange change);

  /**
   * Stores how the run ended: status, counts, final value, points and a COMPLETED settings record,
   * in one transaction.
   */
  void finishRun(long runId, RunEnd end);

  /**
   * Marks runs still STARTED as STOPPED with this reason, recording their last settings as
   * COMPLETED. Used at startup, when no run can still be going.
   *
   * @return how many runs were stopped
   */
  int stopUnfinishedRuns(Instant stoppedAt, String reason);

  Optional<StoredRunDetails> getRun(long runId);

  /** The latest runs of a broker, newest first, without points. */
  RecordedRunList getRuns(String broker);
}
