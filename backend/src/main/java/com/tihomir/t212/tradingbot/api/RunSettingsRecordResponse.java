package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.RunEvent;
import com.tihomir.t212.tradingbot.bot.RunSettingsRecord;
import java.time.Instant;

public record RunSettingsRecordResponse(
    RunEvent event, Instant recordedAt, long settingsId, AlgorithmSettingsResponse settings) {

  static RunSettingsRecordResponse of(RunSettingsRecord record) {
    return new RunSettingsRecordResponse(
        record.event(),
        record.recordedAt(),
        record.settingsId(),
        AlgorithmSettingsResponse.of(record.settings()));
  }
}
