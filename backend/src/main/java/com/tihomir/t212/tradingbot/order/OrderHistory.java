package com.tihomir.t212.tradingbot.order;

import java.util.List;

public record OrderHistory(List<OrderHistoryEntry> orders) {

  public OrderHistory {
    orders = List.copyOf(orders);
  }
}
