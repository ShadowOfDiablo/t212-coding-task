package com.tihomir.t212.tradingbot.bot;

import java.util.List;

public record RunSettingsChangeList(List<RunSettingsChange> changes) {

  public RunSettingsChangeList {
    changes = List.copyOf(changes);
  }
}
