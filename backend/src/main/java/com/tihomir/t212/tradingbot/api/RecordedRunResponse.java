package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.RecordedRun;
import com.tihomir.t212.tradingbot.bot.RunStatus;
import com.tihomir.t212.tradingbot.mode.BotMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record RecordedRunResponse(
    long id,
    BotMode mode,
    String broker,
    String ticker,
    long algorithmId,
    RunStatus status,
    Instant startedAt,
    Optional<Instant> finishedAt,
    Optional<Instant> from,
    Optional<Instant> to,
    BigDecimal startingValue,
    Optional<BigDecimal> finalValue,
    int buyCount,
    int sellCount,
    int stopLossCount,
    int refusedOrderCount,
    Optional<String> failureReason) {

  static RecordedRunResponse of(RecordedRun run) {
    return new RecordedRunResponse(
        run.id(),
        run.mode(),
        run.broker(),
        run.ticker(),
        run.algorithmId(),
        run.status(),
        run.startedAt(),
        run.finishedAt(),
        run.from(),
        run.to(),
        run.startingValue(),
        run.finalValue(),
        run.counts().buyCount(),
        run.counts().sellCount(),
        run.counts().stopLossCount(),
        run.counts().refusedOrderCount(),
        run.failureReason());
  }
}
