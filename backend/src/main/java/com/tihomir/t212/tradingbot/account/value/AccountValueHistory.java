package com.tihomir.t212.tradingbot.account.value;

import java.util.ArrayDeque;
import java.util.Deque;

/** One broker's account values in memory, oldest first; the oldest drop out past the limit. */
final class AccountValueHistory {

  /** One week of one-minute samples. */
  static final int MAX_POINTS = 10_080;

  private final Deque<AccountValuePoint> points = new ArrayDeque<>();

  synchronized void addPoint(AccountValuePoint point) {
    points.addLast(point);
    if (points.size() > MAX_POINTS) {
      points.removeFirst();
    }
  }

  synchronized AccountValuePointList points() {
    return new AccountValuePointList(points.stream().toList());
  }
}
