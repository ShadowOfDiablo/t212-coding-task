package com.tihomir.t212.tradingbot.order;

public interface OrderStore {

  /** Inserts an executed order and its fills in one transaction. */
  void insertExecutedOrder(OrderHistoryEntry order);

  void insertRejectedOrder(RejectedOrder order);

  OpenOrderList getOpenOrders(String broker);

  OrderHistory getOrderHistory(String broker);

  RejectedOrderList getRejectedOrders(String broker);
}
