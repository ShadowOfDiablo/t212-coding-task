package com.tihomir.t212.tradingbot.bot;

import java.util.List;

public record RecordedRunList(List<RecordedRun> runs) {

  public RecordedRunList {
    runs = List.copyOf(runs);
  }
}
