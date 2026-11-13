package com.tihomir.t212.tradingbot.bot;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A training run in memory while it goes and after it ends. Only the training thread writes it; any
 * thread can read it.
 */
public final class RunningTraining {

  private final String broker;
  private final long runId;
  private final List<PortfolioPoint> points = new CopyOnWriteArrayList<>();
  private final AtomicReference<TrainingProgress> progress;
  private volatile boolean finished;

  public RunningTraining(String broker, long runId, BigDecimal startingBalance) {
    this.broker = broker;
    this.runId = runId;
    this.progress = new AtomicReference<>(TrainingProgress.beforeFirstCandle(startingBalance));
  }

  public String broker() {
    return broker;
  }

  public long runId() {
    return runId;
  }

  public TrainingProgress progress() {
    return progress.get();
  }

  public int pointCount() {
    return points.size();
  }

  /** The chart points from this index on; empty when there are none yet. */
  public PortfolioPointList pointsFrom(int firstIndex) {
    List<PortfolioPoint> snapshot = List.copyOf(points);
    if (firstIndex >= snapshot.size()) {
      return PortfolioPointList.NONE;
    }
    return new PortfolioPointList(snapshot.subList(firstIndex, snapshot.size()));
  }

  /** True once the end of the run is recorded, or recording it failed. */
  public boolean isFinished() {
    return finished;
  }

  void recordProgress(TrainingProgress latestProgress) {
    progress.set(latestProgress);
  }

  void addPoint(PortfolioPoint point) {
    points.add(point);
  }

  void markFinished() {
    finished = true;
  }
}
