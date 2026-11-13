package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.RunDetails;
import java.util.List;

public record RunDetailsResponse(
    RecordedRunResponse run,
    AlgorithmSettingsResponse settings,
    List<PortfolioPointResponse> points) {

  static RunDetailsResponse of(RunDetails details) {
    return new RunDetailsResponse(
        RecordedRunResponse.of(details.run()),
        AlgorithmSettingsResponse.of(details.settings()),
        details.points().points().stream().map(PortfolioPointResponse::of).toList());
  }
}
