package com.tihomir.t212.tradingbot.order;

import com.tihomir.t212.tradingbot.order.model.OpenOrderList;
import com.tihomir.t212.tradingbot.order.model.OrderHistory;
import com.tihomir.t212.tradingbot.order.model.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.model.RejectedOrder;
import com.tihomir.t212.tradingbot.order.model.RejectedOrderList;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataAccessResourceFailureException;

public final class FakeOrderStore implements OrderStore {

  private final List<OrderHistoryEntry> executedOrders = new ArrayList<>();
  private final List<RejectedOrder> rejectedOrders = new ArrayList<>();
  private boolean databaseDown;

  @Override
  public void insertExecutedOrder(OrderHistoryEntry order) {
    failIfDatabaseDown();
    executedOrders.add(order);
  }

  @Override
  public void insertRejectedOrder(RejectedOrder order) {
    failIfDatabaseDown();
    rejectedOrders.add(order);
  }

  @Override
  public OpenOrderList getOpenOrders(String broker) {
    failIfDatabaseDown();
    return new OpenOrderList(List.of());
  }

  @Override
  public OrderHistory getOrderHistory(String broker) {
    failIfDatabaseDown();
    return new OrderHistory(executedOrders);
  }

  @Override
  public RejectedOrderList getRejectedOrders(String broker) {
    failIfDatabaseDown();
    return new RejectedOrderList(rejectedOrders);
  }

  void takeDatabaseDown() {
    databaseDown = true;
  }

  private void failIfDatabaseDown() {
    if (databaseDown) {
      throw new DataAccessResourceFailureException("connection refused");
    }
  }
}
