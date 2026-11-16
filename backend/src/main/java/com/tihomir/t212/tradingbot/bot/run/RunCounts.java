package com.tihomir.t212.tradingbot.bot.run;

/** What a run did. Stop-loss and take-profit sells are also counted as sells. */
public record RunCounts(
    int buyCount, int sellCount, int stopLossCount, int takeProfitCount, int refusedOrderCount) {

  public static final RunCounts NONE = new RunCounts(0, 0, 0, 0, 0);
}
