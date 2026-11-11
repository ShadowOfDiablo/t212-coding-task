package com.tihomir.t212.tradingbot.order;

import java.time.Instant;
import java.util.List;

public interface OrderStore {

  /** Records an order before it is sent, so a crash never loses track of it. */
  void insertOpenOrder(OpenOrder order);

  void markOpenOrderStatus(String clientOrderId, OpenOrderStatus status, Instant updatedAt);

  /** Removes an open order that was never sent to the exchange. */
  void deleteOpenOrder(String clientOrderId);

  /** Moves an executed order and its fills into the history in one transaction. */
  void settleExecutedOrder(ExecutedOrder order);

  /** Moves an order the exchange refused into the history in one transaction. */
  void settleRejectedOrder(
      String clientOrderId, ExchangeRejectedRequestException rejection, Instant closedAt);

  List<OpenOrder> openOrders(String broker);

  List<OrderHistoryEntry> orderHistory(String broker);
}
