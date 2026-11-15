package com.tihomir.t212.tradingbot.bot;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.mode.BotMode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class UnfinishedRunCleanupTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void stopsRunsLeftStartedAtStartup() {
    FakeAlgorithmRunStore runStore = new FakeAlgorithmRunStore();
    long runId =
        runStore.insertStartedRun(
            new RunStart(
                BotMode.TRADING,
                "binance",
                "BTCUSDT",
                NOW.minusSeconds(60),
                Optional.empty(),
                Optional.empty(),
                BigDecimal.TEN,
                1,
                SettingsSource.MANUAL));

    new UnfinishedRunCleanup(runStore, Clock.fixed(NOW, ZoneOffset.UTC))
        .run(new DefaultApplicationArguments());

    RecordedRun run = runStore.getRun(runId).orElseThrow().run();
    assertThat(run.status()).isEqualTo(RunStatus.STOPPED);
    assertThat(run.failureReason()).contains(UnfinishedRunCleanup.INTERRUPTED_REASON);
  }
}
