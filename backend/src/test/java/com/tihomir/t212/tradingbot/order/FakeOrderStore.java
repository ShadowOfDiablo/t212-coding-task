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
  private final List<String> settledOrders = new ArrayList<>();
  private final List<String> rejectedOrders = new ArrayList<>();
  private boolean databaseDown;
  private boolean settlingFails;

  @Override
  public void insertOpenOrder(OpenOrder order) {
    if (databaseDown) {
      throw new DataAccessResourceFailureException("connection refused");
    }
    openOrders.put(order.clientOrderId(), order);
  }

  @Override
  public void markOpenOrderStatus(String clientOrderId, OpenOrderStatus status, Instant updatedAt) {
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
  public void settleExecutedOrder(ExecutedOrder order) {
    if (settlingFails) {
      throw new DataAccessResourceFailureException("connection lost");
    }
    openOrders.remove(order.clientOrderId());
    settledOrders.add(order.clientOrderId());
  }

  @Override
  public void settleRejectedOrder(
      String clientOrderId, ExchangeRejectedRequestException rejection, Instant closedAt) {
    openOrders.remove(clientOrderId);
    rejectedOrders.add(clientOrderId);
  }

  @Override
  public List<OpenOrder> openOrders(String broker) {
    return List.copyOf(openOrders.values());
  }

  @Override
  public List<OrderHistoryEntry> orderHistory(String broker) {
    return List.of();
  }

  Optional<OpenOrder> onlyOpenOrder() {
    return openOrders.values().stream().findFirst();
  }

  List<String> settledOrders() {
    return List.copyOf(settledOrders);
  }

  List<String> rejectedOrders() {
    return List.copyOf(rejectedOrders);
  }

  void takeDatabaseDown() {
    databaseDown = true;
  }

  void failSettling() {
    settlingFails = true;
  }
}
