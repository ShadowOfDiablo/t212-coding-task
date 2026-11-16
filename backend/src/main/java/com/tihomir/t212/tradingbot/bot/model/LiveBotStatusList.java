package com.tihomir.t212.tradingbot.bot.model;

import java.util.List;

public record LiveBotStatusList(List<LiveBotStatus> bots) {

  public LiveBotStatusList {
    bots = List.copyOf(bots);
  }
}
