package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OpenOrder;
import com.tihomir.t212.tradingbot.order.OpenOrderStatus;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.OrderSide;
import com.tihomir.t212.tradingbot.order.OrderStore;
import com.tihomir.t212.tradingbot.order.RejectedOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OrderRepository implements OrderStore {

  private static final int MAX_ORDERS_PER_HISTORY_REQUEST = 100;
  private static final int ONE_ROW = 1;

  private final JdbcClient jdbcClient;

  public OrderRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void insertOpenOrder(OpenOrder order) {
    jdbcClient
        .sql(
            """
            INSERT INTO open_order (client_order_id, broker, ticker, side, order_type, amount,
                                    amount_type, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(order.clientOrderId())
        .param(order.broker())
        .param(order.ticker())
        .param(order.side().name())
        .param(order.orderType())
        .param(order.amount())
        .param(order.amountType().name())
        .param(order.status().name())
        .param(timestamp(order.createdAt()))
        .param(timestamp(order.updatedAt()))
        .update();
  }

  @Override
  public void updateOpenOrderStatus(
      String clientOrderId, OpenOrderStatus status, Instant updatedAt) {
    jdbcClient
        .sql("UPDATE open_order SET status = ?, updated_at = ? WHERE client_order_id = ?")
        .param(status.name())
        .param(timestamp(updatedAt))
        .param(clientOrderId)
        .update();
  }

  @Override
  public void deleteOpenOrder(String clientOrderId) {
    jdbcClient
        .sql("DELETE FROM open_order WHERE client_order_id = ?")
        .param(clientOrderId)
        .update();
  }

  @Override
  @Transactional
  public void moveExecutedOrderToHistory(ExecutedOrder order) {
    int moved =
        jdbcClient
            .sql(
                """
                INSERT INTO order_history (client_order_id, exchange_order_id, broker, ticker,
                                           side, order_type, amount, amount_type, final_status,
                                           executed_quantity, quote_amount, average_price,
                                           created_at, closed_at)
                SELECT client_order_id, ?, broker, ticker, side, order_type, amount, amount_type,
                       ?, ?, ?, ?, created_at, ?
                FROM open_order
                WHERE client_order_id = ?
                """)
            .param(order.exchangeOrderId())
            .param(order.exchangeStatus())
            .param(order.executedQuantity())
            .param(order.quoteAmount())
            .param(order.averagePrice())
            .param(timestamp(order.transactTime()))
            .param(order.clientOrderId())
            .update();
    if (moved == ONE_ROW) {
      order.fills().forEach(fill -> insertFill(order.clientOrderId(), fill));
      deleteOpenOrder(order.clientOrderId());
    }
  }

  @Override
  @Transactional
  public void moveOrderToRejectedOrders(
      String clientOrderId, ExchangeRejectedRequestException rejection, Instant rejectedAt) {
    int moved =
        jdbcClient
            .sql(
                """
                INSERT INTO rejected_order (client_order_id, broker, ticker, side, order_type,
                                            amount, amount_type, error_code, error_message,
                                            created_at, rejected_at)
                SELECT client_order_id, broker, ticker, side, order_type, amount, amount_type,
                       ?, ?, created_at, ?
                FROM open_order
                WHERE client_order_id = ?
                """)
            .param(rejection.exchangeErrorCode())
            .param(rejection.getMessage())
            .param(timestamp(rejectedAt))
            .param(clientOrderId)
            .update();
    if (moved == ONE_ROW) {
      deleteOpenOrder(clientOrderId);
    }
  }

  @Override
  public List<OpenOrder> getOpenOrders(String broker) {
    return jdbcClient
        .sql("SELECT * FROM open_order WHERE broker = ? ORDER BY created_at")
        .param(broker)
        .query(OrderRepository::openOrder)
        .list();
  }

  @Override
  public List<OrderHistoryEntry> getOrderHistory(String broker) {
    List<OrderHistoryEntry> orders =
        jdbcClient
            .sql("SELECT * FROM order_history WHERE broker = ? ORDER BY closed_at DESC LIMIT ?")
            .param(broker)
            .param(MAX_ORDERS_PER_HISTORY_REQUEST)
            .query(OrderRepository::orderHistoryEntryWithoutFills)
            .list();
    if (orders.isEmpty()) {
      return orders;
    }
    Map<String, List<OrderFill>> fillsByOrder = fillsClosedSince(broker, orders.getLast());
    return orders.stream()
        .map(order -> withFills(order, fillsByOrder.getOrDefault(order.clientOrderId(), List.of())))
        .toList();
  }

  @Override
  public List<RejectedOrder> getRejectedOrders(String broker) {
    return jdbcClient
        .sql("SELECT * FROM rejected_order WHERE broker = ? ORDER BY rejected_at DESC LIMIT ?")
        .param(broker)
        .param(MAX_ORDERS_PER_HISTORY_REQUEST)
        .query(OrderRepository::rejectedOrder)
        .list();
  }

  private Map<String, List<OrderFill>> fillsClosedSince(String broker, OrderHistoryEntry oldest) {
    return jdbcClient
        .sql(
            """
            SELECT f.*
            FROM order_fill f
            JOIN order_history h ON h.client_order_id = f.client_order_id
            WHERE h.broker = ? AND h.closed_at >= ?
            ORDER BY f.trade_id
            """)
        .param(broker)
        .param(timestamp(oldest.closedAt()))
        .query(OrderRepository::orderFillRow)
        .list()
        .stream()
        .collect(
            Collectors.groupingBy(
                OrderFillRow::clientOrderId,
                Collectors.mapping(OrderFillRow::fill, Collectors.toList())));
  }

  private void insertFill(String clientOrderId, OrderFill fill) {
    jdbcClient
        .sql(
            """
            INSERT INTO order_fill (client_order_id, trade_id, price, quantity, commission,
                                    commission_asset)
            VALUES (?, ?, ?, ?, ?, ?)
            """)
        .param(clientOrderId)
        .param(fill.tradeId())
        .param(fill.price())
        .param(fill.quantity())
        .param(fill.commission())
        .param(fill.commissionAsset())
        .update();
  }

  private static OpenOrder openOrder(ResultSet row, int rowNumber) throws SQLException {
    return new OpenOrder(
        row.getString("client_order_id"),
        row.getString("broker"),
        row.getString("ticker"),
        OrderSide.valueOf(row.getString("side")),
        row.getString("order_type"),
        row.getBigDecimal("amount"),
        OrderAmountType.valueOf(row.getString("amount_type")),
        OpenOrderStatus.valueOf(row.getString("status")),
        instant(row, "created_at"),
        instant(row, "updated_at"));
  }

  private static OrderHistoryEntry orderHistoryEntryWithoutFills(ResultSet row, int rowNumber)
      throws SQLException {
    return new OrderHistoryEntry(
        row.getString("client_order_id"),
        row.getLong("exchange_order_id"),
        row.getString("broker"),
        row.getString("ticker"),
        OrderSide.valueOf(row.getString("side")),
        row.getString("order_type"),
        row.getBigDecimal("amount"),
        OrderAmountType.valueOf(row.getString("amount_type")),
        row.getString("final_status"),
        row.getBigDecimal("executed_quantity"),
        row.getBigDecimal("quote_amount"),
        row.getBigDecimal("average_price"),
        instant(row, "created_at"),
        instant(row, "closed_at"),
        List.of());
  }

  private static OrderHistoryEntry withFills(OrderHistoryEntry order, List<OrderFill> fills) {
    return new OrderHistoryEntry(
        order.clientOrderId(),
        order.exchangeOrderId(),
        order.broker(),
        order.ticker(),
        order.side(),
        order.orderType(),
        order.amount(),
        order.amountType(),
        order.finalStatus(),
        order.executedQuantity(),
        order.quoteAmount(),
        order.averagePrice(),
        order.createdAt(),
        order.closedAt(),
        fills);
  }

  private static RejectedOrder rejectedOrder(ResultSet row, int rowNumber) throws SQLException {
    return new RejectedOrder(
        row.getString("client_order_id"),
        row.getString("broker"),
        row.getString("ticker"),
        OrderSide.valueOf(row.getString("side")),
        row.getString("order_type"),
        row.getBigDecimal("amount"),
        OrderAmountType.valueOf(row.getString("amount_type")),
        row.getInt("error_code"),
        row.getString("error_message"),
        instant(row, "created_at"),
        instant(row, "rejected_at"));
  }

  private static OrderFillRow orderFillRow(ResultSet row, int rowNumber) throws SQLException {
    return new OrderFillRow(
        row.getString("client_order_id"),
        new OrderFill(
            row.getLong("trade_id"),
            row.getBigDecimal("price"),
            row.getBigDecimal("quantity"),
            row.getBigDecimal("commission"),
            row.getString("commission_asset")));
  }

  private static OffsetDateTime timestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    return row.getObject(column, OffsetDateTime.class).toInstant();
  }
}
