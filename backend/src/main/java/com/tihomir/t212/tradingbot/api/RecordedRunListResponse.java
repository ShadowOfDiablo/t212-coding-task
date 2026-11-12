package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.RecordedRunList;
import java.util.List;

public record RecordedRunListResponse(List<RecordedRunResponse> runs) {

  static RecordedRunListResponse of(RecordedRunList runs) {
    return new RecordedRunListResponse(runs.runs().stream().map(RecordedRunResponse::of).toList());
  }
}
