package com.tihomir.t212.tradingbot.api;

/** Whether a training run is going; while it does, its settings can't be edited. */
public record TrainingStatusResponse(boolean running) {}
