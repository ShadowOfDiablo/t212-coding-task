package com.tihomir.t212.tradingbot.bot.run;

import java.util.List;

public record PricePointList(List<PricePoint> points) {

  public PricePointList {
    points = List.copyOf(points);
  }
}
