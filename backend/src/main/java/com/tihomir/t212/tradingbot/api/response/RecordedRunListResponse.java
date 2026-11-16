package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.bot.run.RecordedRunList;
import java.util.List;

public record RecordedRunListResponse(List<RecordedRunResponse> runs) {

  public static RecordedRunListResponse of(RecordedRunList runs) {
    return new RecordedRunListResponse(runs.runs().stream().map(RecordedRunResponse::of).toList());
  }
}
