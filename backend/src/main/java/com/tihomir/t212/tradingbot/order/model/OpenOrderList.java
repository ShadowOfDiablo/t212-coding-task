package com.tihomir.t212.tradingbot.order.model;

import java.util.List;

public record OpenOrderList(List<OpenOrder> openOrders) {

  public OpenOrderList {
    openOrders = List.copyOf(openOrders);
  }
}
