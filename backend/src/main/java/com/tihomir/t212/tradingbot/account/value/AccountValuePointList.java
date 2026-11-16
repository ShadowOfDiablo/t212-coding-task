package com.tihomir.t212.tradingbot.account.value;

import java.util.List;

/** Oldest first. */
public record AccountValuePointList(List<AccountValuePoint> points) {

  public AccountValuePointList {
    points = List.copyOf(points);
  }
}
