package com.tihomir.t212.tradingbot.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessResourceFailureException;

final class FakeOrderStore implements OrderStore {

  private final Map<String, OpenOrder> openOrders = new LinkedHashMap<>();
  private final List<String> executedOrdersInHistory = new ArrayList<>();
  private final List<String> rejectedOrdersInHistory = new ArrayList<>();
  private boolean databaseDown;
  private boolean movingToHistoryFails;

  @Override
  public void insertOpenOrder(OpenOrder order) {
    if (databaseDown) {
      throw new DataAccessResourceFailureException("connection refused");
    }
    openOrders.put(order.clientOrderId(), order);
  }

  @Override
  public void updateOpenOrderStatus(
      String clientOrderId, OpenOrderStatus status, Instant updatedAt) {
    OpenOrder order = openOrders.get(clientOrderId);
    openOrders.put(
        clientOrderId,
        new OpenOrder(
            order.clientOrderId(),
            order.broker(),
            order.ticker(),
            order.side(),
            order.orderType(),
            order.amount(),
            order.amountType(),
            status,
            order.createdAt(),
            updatedAt));
  }

  @Override
  public void deleteOpenOrder(String clientOrderId) {
    openOrders.remove(clientOrderId);
  }

  @Override
  public void moveExecutedOrderToHistory(ExecutedOrder order) {
    if (movingToHistoryFails) {
      throw new DataAccessResourceFailureException("connection lost");
    }
    openOrders.remove(order.clientOrderId());
    executedOrdersInHistory.add(order.clientOrderId());
  }

  @Override
  public void moveRejectedOrderToHistory(
      String clientOrderId, ExchangeRejectedRequestException rejection, Instant closedAt) {
    openOrders.remove(clientOrderId);
    rejectedOrdersInHistory.add(clientOrderId);
  }

  @Override
  public List<OpenOrder> getOpenOrders(String broker) {
    return List.copyOf(openOrders.values());
  }

  @Override
  public List<OrderHistoryEntry> getOrderHistory(String broker) {
    return List.of();
  }

  Optional<OpenOrder> onlyOpenOrder() {
    return openOrders.values().stream().findFirst();
  }

  List<String> executedOrdersInHistory() {
    return List.copyOf(executedOrdersInHistory);
  }

  List<String> rejectedOrdersInHistory() {
    return List.copyOf(rejectedOrdersInHistory);
  }

  void takeDatabaseDown() {
    databaseDown = true;
  }

  void failMovingToHistory() {
    movingToHistoryFails = true;
  }
}
