package com.tihomir.t212.tradingbot.bot.tickersettings;

public interface TickerSettingsStore {

  /** Every ticker of the broker that has assigned settings, in no particular order. */
  TickerSettingsList getAssignedSettings(String broker);

  /** Assigns the settings to the ticker, replacing what it had. */
  void saveAssignedSettings(String broker, String ticker, AssignedSettings assigned);
}
