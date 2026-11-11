package com.tihomir.t212.tradingbot.order;

import java.time.Instant;
import java.util.List;

public interface OrderStore {

  /** Records an order before it is sent, so a crash never loses track of it. */
  void insertOpenOrder(OpenOrder order);

  void updateOpenOrderStatus(String clientOrderId, OpenOrderStatus status, Instant updatedAt);

  /** Removes an open order that was never sent to the exchange. */
  void deleteOpenOrder(String clientOrderId);

  /** Moves an executed order and its fills into the history in one transaction. */
  void moveExecutedOrderToHistory(ExecutedOrder order);

  /** Moves an order the exchange refused into the rejected orders in one transaction. */
  void moveOrderToRejectedOrders(
      String clientOrderId, ExchangeRejectedRequestException rejection, Instant rejectedAt);

  List<OpenOrder> getOpenOrders(String broker);

  List<OrderHistoryEntry> getOrderHistory(String broker);

  List<RejectedOrder> getRejectedOrders(String broker);
}
