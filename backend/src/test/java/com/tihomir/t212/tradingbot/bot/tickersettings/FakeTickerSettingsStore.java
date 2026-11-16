package com.tihomir.t212.tradingbot.bot.tickersettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FakeTickerSettingsStore implements TickerSettingsStore {

  private final List<TickerSettings> assigned = new ArrayList<>();

  @Override
  public synchronized TickerSettingsList getAssignedSettings(String broker) {
    return new TickerSettingsList(assigned);
  }

  @Override
  public synchronized void saveAssignedSettings(
      String broker, String ticker, AssignedSettings settings) {
    assigned.removeIf(existing -> existing.ticker().equals(ticker));
    assigned.add(new TickerSettings(ticker, Optional.of(settings)));
  }
}
