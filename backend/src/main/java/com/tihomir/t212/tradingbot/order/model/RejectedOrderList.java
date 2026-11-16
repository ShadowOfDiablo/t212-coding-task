package com.tihomir.t212.tradingbot.order.model;

import java.util.List;

public record RejectedOrderList(List<RejectedOrder> rejectedOrders) {

  public RejectedOrderList {
    rejectedOrders = List.copyOf(rejectedOrders);
  }
}
