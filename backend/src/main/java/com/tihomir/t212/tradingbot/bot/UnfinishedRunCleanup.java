package com.tihomir.t212.tradingbot.bot;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * At startup no run can still be going, so runs left STARTED by a crash or a restart are marked
 * STOPPED. A live bot does not resume by itself.
 */
@Component
public class UnfinishedRunCleanup implements ApplicationRunner {

  static final String INTERRUPTED_REASON = "Interrupted by a restart";

  private static final Logger log = LoggerFactory.getLogger(UnfinishedRunCleanup.class);

  private final AlgorithmRunStore runStore;
  private final Clock clock;

  public UnfinishedRunCleanup(AlgorithmRunStore runStore, Clock clock) {
    this.runStore = runStore;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    try {
      int stoppedRuns = runStore.stopUnfinishedRuns(clock.instant(), INTERRUPTED_REASON);
      if (stoppedRuns > 0) {
        log.warn("Marked {} run(s) left unfinished by the last shutdown as STOPPED", stoppedRuns);
      }
    } catch (DataAccessException e) {
      log.error("Could not mark unfinished runs as stopped: {}", e.getMessage(), e);
    }
  }
}
