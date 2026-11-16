package com.tihomir.t212.tradingbot.order;

import com.tihomir.t212.tradingbot.order.model.OpenOrderList;
import com.tihomir.t212.tradingbot.order.model.OrderHistory;
import com.tihomir.t212.tradingbot.order.model.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.model.RejectedOrder;
import com.tihomir.t212.tradingbot.order.model.RejectedOrderList;

public interface OrderStore {

  /** Inserts an executed order and its fills in one transaction. */
  void insertExecutedOrder(OrderHistoryEntry order);

  void insertRejectedOrder(RejectedOrder order);

  OpenOrderList getOpenOrders(String broker);

  OrderHistory getOrderHistory(String broker);

  RejectedOrderList getRejectedOrders(String broker);
}
