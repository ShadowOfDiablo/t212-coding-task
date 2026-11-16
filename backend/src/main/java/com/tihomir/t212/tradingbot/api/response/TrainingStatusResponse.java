package com.tihomir.t212.tradingbot.api.response;

import java.util.Optional;

/**
 * Whether a training run or a tuning is going; while one does, its settings can't be edited.
 *
 * @param tuningProgressPercent the share of tuning tries done; empty unless a tuning goes on
 */
public record TrainingStatusResponse(boolean running, Optional<Integer> tuningProgressPercent) {}
