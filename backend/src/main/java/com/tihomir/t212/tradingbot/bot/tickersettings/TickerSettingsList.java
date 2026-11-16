package com.tihomir.t212.tradingbot.bot.tickersettings;

import java.util.List;
import java.util.Optional;

public record TickerSettingsList(List<TickerSettings> tickers) {

  public TickerSettingsList {
    tickers = List.copyOf(tickers);
  }

  public Optional<AssignedSettings> assignedTo(String ticker) {
    return tickers.stream()
        .filter(settings -> settings.ticker().equals(ticker))
        .findFirst()
        .flatMap(TickerSettings::assigned);
  }
}
