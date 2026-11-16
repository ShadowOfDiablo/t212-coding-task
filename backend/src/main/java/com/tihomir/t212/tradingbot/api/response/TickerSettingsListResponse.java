package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettingsList;
import java.util.List;

public record TickerSettingsListResponse(List<TickerSettingsResponse> tickers) {

  public static TickerSettingsListResponse of(TickerSettingsList list) {
    return new TickerSettingsListResponse(
        list.tickers().stream().map(TickerSettingsResponse::of).toList());
  }
}
