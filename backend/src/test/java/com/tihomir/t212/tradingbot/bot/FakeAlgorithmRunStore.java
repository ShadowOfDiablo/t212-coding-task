package com.tihomir.t212.tradingbot.bot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps runs in memory with the same rules as the database: a run ends only once. */
public final class FakeAlgorithmRunStore implements AlgorithmRunStore {

  private final Map<Long, RecordedRun> runs = new HashMap<>();
  private final Map<Long, List<RunSettingsChange>> changes = new HashMap<>();
  private final Map<Long, PortfolioPointList> points = new HashMap<>();
  private final Map<Long, Long> lastSettingsIds = new HashMap<>();

  @Override
  public synchronized long insertStartedRun(RunStart start) {
    long runId = runs.size() + 1L;
    runs.put(
        runId,
        new RecordedRun(
            runId,
            start.mode(),
            start.broker(),
            start.ticker(),
            RunStatus.STARTED,
            start.startedAt(),
            Optional.empty(),
            start.from(),
            start.to(),
            start.startingValue(),
            Optional.empty(),
            RunCounts.NONE,
            Optional.empty()));
    points.put(runId, PortfolioPointList.NONE);
    insertSettingsChange(
        runId, new RunSettingsChange(RunEvent.STARTED, start.startedAt(), start.settingsId()));
    return runId;
  }

  @Override
  public synchronized void insertSettingsChange(long runId, RunSettingsChange change) {
    changes.computeIfAbsent(runId, id -> new ArrayList<>()).add(change);
    lastSettingsIds.put(runId, change.settingsId());
  }

  @Override
  public synchronized void finishRun(long runId, RunEnd end) {
    RecordedRun run = runs.get(runId);
    if (run == null || run.status() != RunStatus.STARTED) {
      throw new IllegalStateException("Run " + runId + " is not running");
    }
    runs.put(runId, finished(run, end));
    points.put(runId, end.points());
    insertSettingsChange(
        runId, new RunSettingsChange(RunEvent.COMPLETED, end.finishedAt(), end.settingsId()));
  }

  @Override
  public synchronized int stopUnfinishedRuns(Instant stoppedAt, String reason) {
    List<Long> unfinished =
        runs.values().stream()
            .filter(run -> run.status() == RunStatus.STARTED)
            .map(RecordedRun::id)
            .toList();
    unfinished.forEach(runId -> stopRun(runId, stoppedAt, reason));
    return unfinished.size();
  }

  @Override
  public synchronized Optional<StoredRunDetails> getRun(long runId) {
    return Optional.ofNullable(runs.get(runId)).map(this::details);
  }

  private StoredRunDetails details(RecordedRun run) {
    return new StoredRunDetails(
        run, new RunSettingsChangeList(changes.get(run.id())), points.get(run.id()));
  }

  @Override
  public synchronized RecordedRunList getRuns(String broker) {
    return new RecordedRunList(
        runs.values().stream().filter(run -> run.broker().equals(broker)).toList());
  }

  private void stopRun(long runId, Instant stoppedAt, String reason) {
    finishRun(
        runId,
        new RunEnd(
            RunStatus.STOPPED,
            stoppedAt,
            Optional.empty(),
            RunCounts.NONE,
            Optional.of(reason),
            lastSettingsIds.get(runId),
            PortfolioPointList.NONE));
  }

  private static RecordedRun finished(RecordedRun run, RunEnd end) {
    return new RecordedRun(
        run.id(),
        run.mode(),
        run.broker(),
        run.ticker(),
        end.status(),
        run.startedAt(),
        Optional.of(end.finishedAt()),
        run.from(),
        run.to(),
        run.startingValue(),
        end.finalValue(),
        end.counts(),
        end.failureReason());
  }
}
