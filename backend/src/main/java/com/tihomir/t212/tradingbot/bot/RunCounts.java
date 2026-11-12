package com.tihomir.t212.tradingbot.bot;

/** What a run did. Stop-loss sells are also counted as sells. */
public record RunCounts(int buyCount, int sellCount, int stopLossCount, int refusedOrderCount) {

  public static final RunCounts NONE = new RunCounts(0, 0, 0, 0);
}
