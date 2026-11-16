package com.tihomir.t212.tradingbot.api.response;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * @param run the best try, recorded over the whole range
 * @param bestTuneReturnPercent the best try's return on the tune range; much more than its return
 *     on the check range means the settings were overfitted
 * @param baseRun the request's own settings over the same range, not stored, to compare with
 */
public record TuningResponse(
    RunDetailsResponse run,
    BigDecimal bestTuneReturnPercent,
    int trialCount,
    Optional<UnrecordedRunResponse> baseRun) {}
