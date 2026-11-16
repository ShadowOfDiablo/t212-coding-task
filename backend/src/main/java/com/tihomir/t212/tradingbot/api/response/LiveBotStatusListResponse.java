package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.bot.model.LiveBotStatusList;
import java.util.List;

public record LiveBotStatusListResponse(List<LiveBotStatusResponse> bots) {

  public static LiveBotStatusListResponse of(LiveBotStatusList list) {
    return new LiveBotStatusListResponse(
        list.bots().stream().map(LiveBotStatusResponse::running).toList());
  }
}
