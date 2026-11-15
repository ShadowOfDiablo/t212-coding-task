package com.tihomir.t212.tradingbot.api;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * @param run the best try, recorded on the check range
 * @param bestTuneReturnPercent the best try's return on the tune range; much more than the recorded
 *     run's return means the settings were overfitted
 * @param baseRun the request's own settings on the same check range, not stored, to compare with
 */
public record TuningResponse(
    RunDetailsResponse run,
    BigDecimal bestTuneReturnPercent,
    int trialCount,
    Optional<UnrecordedRunResponse> baseRun) {}
