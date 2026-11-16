package com.tihomir.t212.tradingbot.bot.run;

import java.util.List;

public record RecordedRunList(List<RecordedRun> runs) {

  public RecordedRunList {
    runs = List.copyOf(runs);
  }
}
