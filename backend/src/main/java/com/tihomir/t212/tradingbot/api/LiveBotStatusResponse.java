package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.LiveBotStatus;
import java.util.List;
import java.util.Optional;

/** The live bot of a broker; the run fields are JSON {@code null} while it doesn't run. */
public record LiveBotStatusResponse(
    Optional<Long> runId,
    Optional<String> ticker,
    Optional<AlgorithmSettingsResponse> currentParameters,
    Optional<Long> algorithmId,
    int buyCount,
    int sellCount,
    int stopLossCount,
    int takeProfitCount,
    int refusedOrderCount,
    List<PortfolioPointResponse> points) {

  private static final LiveBotStatusResponse NOT_RUNNING =
      new LiveBotStatusResponse(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          0,
          0,
          0,
          0,
          0,
          List.of());

  static LiveBotStatusResponse of(Optional<LiveBotStatus> status) {
    return status.map(LiveBotStatusResponse::running).orElse(NOT_RUNNING);
  }

  static LiveBotStatusResponse running(LiveBotStatus status) {
    return new LiveBotStatusResponse(
        Optional.of(status.runId()),
        Optional.of(status.ticker()),
        Optional.of(AlgorithmSettingsResponse.of(status.currentParameters())),
        Optional.of(status.algorithmId()),
        status.counts().buyCount(),
        status.counts().sellCount(),
        status.counts().stopLossCount(),
        status.counts().takeProfitCount(),
        status.counts().refusedOrderCount(),
        status.points().points().stream().map(PortfolioPointResponse::of).toList());
  }
}
