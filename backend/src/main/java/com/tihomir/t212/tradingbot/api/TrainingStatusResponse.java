package com.tihomir.t212.tradingbot.api;

import java.util.Optional;

/**
 * Whether a training run is going; while it does, its settings can't be edited.
 *
 * @param runId the run going on; empty when none is
 */
public record TrainingStatusResponse(
    boolean running, boolean areParametersEditable, Optional<Long> runId) {}
