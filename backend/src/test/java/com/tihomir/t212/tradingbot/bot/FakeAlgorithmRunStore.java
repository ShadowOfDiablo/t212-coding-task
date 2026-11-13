package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.position.PositionWriteList;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps runs in memory with the same rules as the database: a run ends only once. */
public final class FakeAlgorithmRunStore implements AlgorithmRunStore {

  private final Map<Long, RecordedRun> runs = new HashMap<>();
  private final Map<Long, PortfolioPointList> points = new HashMap<>();
  private final Map<Long, PositionWriteList> positionWrites = new HashMap<>();
  private final Map<String, Long> activeAlgorithmIds = new HashMap<>();

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
            start.algorithmId(),
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
    if (start.mode() == BotMode.TRADING) {
      activeAlgorithmIds.put(start.ticker(), start.algorithmId());
    }
    return runId;
  }

  @Override
  public synchronized void finishRun(long runId, RunEnd end) {
    RecordedRun run = runs.get(runId);
    if (run == null || run.status() != RunStatus.STARTED) {
      throw new IllegalStateException("Run " + runId + " is not running");
    }
    runs.put(runId, finished(run, end));
    points.put(runId, end.points());
    positionWrites.put(runId, end.positionWrites());
  }

  @Override
  public synchronized long replaceRun(long runId, RunEnd end, RunStart next) {
    finishRun(runId, end);
    return insertStartedRun(next);
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

  @Override
  public synchronized RecordedRunList getRuns(String broker) {
    return new RecordedRunList(
        runs.values().stream().filter(run -> run.broker().equals(broker)).toList());
  }

  /** The position writes a run ended with; none while it runs. */
  public synchronized PositionWriteList positionWrites(long runId) {
    return positionWrites.getOrDefault(runId, PositionWriteList.NONE);
  }

  /** The variant the last live run of the ticker started with. */
  public synchronized Optional<Long> activeAlgorithmId(String ticker) {
    return Optional.ofNullable(activeAlgorithmIds.get(ticker));
  }

  private StoredRunDetails details(RecordedRun run) {
    return new StoredRunDetails(run, points.get(run.id()));
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
            PortfolioPointList.NONE,
            PositionWriteList.NONE));
  }

  private static RecordedRun finished(RecordedRun run, RunEnd end) {
    return new RecordedRun(
        run.id(),
        run.mode(),
        run.broker(),
        run.ticker(),
        run.algorithmId(),
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
