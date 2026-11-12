package com.tihomir.t212.tradingbot.bot;

import java.util.List;

public record PortfolioPointList(List<PortfolioPoint> points) {

  public static final PortfolioPointList NONE = new PortfolioPointList(List.of());

  public PortfolioPointList {
    points = List.copyOf(points);
  }
}
