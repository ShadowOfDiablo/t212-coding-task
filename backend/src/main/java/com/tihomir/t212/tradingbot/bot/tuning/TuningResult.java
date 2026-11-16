package com.tihomir.t212.tradingbot.bot.tuning;

import com.tihomir.t212.tradingbot.bot.run.UnrecordedRun;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * @param runId the recorded run: the best try, replayed over the whole range
 * @param bestTuneReturnPercent the best try's return on the tune range
 * @param baseRun the base settings replayed over the whole range, not stored; empty if that replay
 *     failed
 */
public record TuningResult(
    long runId,
    BigDecimal bestTuneReturnPercent,
    int trialCount,
    Optional<UnrecordedRun> baseRun) {}
