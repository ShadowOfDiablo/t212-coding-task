package com.tihomir.t212.tradingbot.bot;

import java.util.List;

public record RunSettingsHistory(List<RunSettingsRecord> records) {

  public RunSettingsHistory {
    records = List.copyOf(records);
  }
}
