package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.RunDetails;
import java.util.List;

public record RunDetailsResponse(
    RecordedRunResponse run,
    List<RunSettingsRecordResponse> settingsHistory,
    List<PortfolioPointResponse> points) {

  static RunDetailsResponse of(RunDetails details) {
    return new RunDetailsResponse(
        RecordedRunResponse.of(details.run()),
        details.settingsHistory().records().stream().map(RunSettingsRecordResponse::of).toList(),
        details.points().points().stream().map(PortfolioPointResponse::of).toList());
  }
}
