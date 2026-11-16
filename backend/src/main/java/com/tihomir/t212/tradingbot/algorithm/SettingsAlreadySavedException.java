package com.tihomir.t212.tradingbot.algorithm;

public class SettingsAlreadySavedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SettingsAlreadySavedException(String savedName) {
    super("These settings are already saved as \"" + savedName + "\"");
  }
}
