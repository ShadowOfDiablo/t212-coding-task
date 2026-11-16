package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.bot.run.RunDetails;
import java.util.List;

public record RunDetailsResponse(
    RecordedRunResponse run,
    AlgorithmSettingsResponse settings,
    List<PortfolioPointResponse> points) {

  public static RunDetailsResponse of(RunDetails details) {
    return new RunDetailsResponse(
        RecordedRunResponse.of(details.run()),
        AlgorithmSettingsResponse.of(details.settings()),
        details.points().points().stream().map(PortfolioPointResponse::of).toList());
  }
}
