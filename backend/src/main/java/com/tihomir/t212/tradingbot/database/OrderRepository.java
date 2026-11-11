package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.order.OpenOrder;
import com.tihomir.t212.tradingbot.order.OpenOrderList;
import com.tihomir.t212.tradingbot.order.OpenOrderStatus;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.OrderHistory;
import com.tihomir.t212.tradingbot.order.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.OrderSide;
import com.tihomir.t212.tradingbot.order.OrderStore;
import com.tihomir.t212.tradingbot.order.RejectedOrder;
import com.tihomir.t212.tradingbot.order.RejectedOrderList;
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

  private final JdbcClient jdbcClient;

  public OrderRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  @Transactional
  public void insertExecutedOrder(OrderHistoryEntry order) {
    long alreadyStored =
        jdbcClient
            .sql("SELECT COUNT(*) FROM order_history WHERE exchange_order_id = ?")
            .param(order.exchangeOrderId())
            .query(Long.class)
            .single();
    if (alreadyStored > 0) {
      return;
    }
    jdbcClient
        .sql(
            """
            INSERT INTO order_history (exchange_order_id, client_order_id, broker, ticker, side,
                                       order_type, amount, amount_type, final_status,
                                       executed_quantity, quote_amount, average_price,
                                       created_at, closed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(order.exchangeOrderId())
        .param(order.clientOrderId())
        .param(order.broker())
        .param(order.ticker())
        .param(order.side().name())
        .param(order.orderType())
        .param(order.amount())
        .param(order.amountType().name())
        .param(order.finalStatus())
        .param(order.executedQuantity())
        .param(order.quoteAmount())
        .param(order.averagePrice())
        .param(timestamp(order.createdAt()))
        .param(timestamp(order.closedAt()))
        .update();
    order.fills().forEach(fill -> insertFill(order.exchangeOrderId(), fill));
  }

  @Override
  public void insertRejectedOrder(RejectedOrder order) {
    jdbcClient
        .sql(
            """
            INSERT INTO rejected_order (broker, ticker, side, order_type, amount, amount_type,
                                        error_code, error_message, rejected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(order.broker())
        .param(order.ticker())
        .param(order.side().name())
        .param(order.orderType())
        .param(order.amount())
        .param(order.amountType().name())
        .param(order.errorCode())
        .param(order.errorMessage())
        .param(timestamp(order.rejectedAt()))
        .update();
  }

  @Override
  public OpenOrderList getOpenOrders(String broker) {
    return new OpenOrderList(
        jdbcClient
            .sql("SELECT * FROM open_order WHERE broker = ? ORDER BY created_at")
            .param(broker)
            .query(OrderRepository::openOrder)
            .list());
  }

  @Override
  public OrderHistory getOrderHistory(String broker) {
    List<OrderHistoryEntry> orders =
        jdbcClient
            .sql("SELECT * FROM order_history WHERE broker = ? ORDER BY closed_at DESC LIMIT ?")
            .param(broker)
            .param(MAX_ORDERS_PER_HISTORY_REQUEST)
            .query(OrderRepository::orderHistoryEntryWithoutFills)
            .list();
    if (orders.isEmpty()) {
      return new OrderHistory(orders);
    }
    Map<Long, List<OrderFill>> fillsByOrder = fillsClosedSince(broker, orders.getLast());
    return new OrderHistory(
        orders.stream()
            .map(order ->
                withFills(order, fillsByOrder.getOrDefault(order.exchangeOrderId(), List.of())))
            .toList());
  }

  @Override
  public RejectedOrderList getRejectedOrders(String broker) {
    return new RejectedOrderList(
        jdbcClient
            .sql(
                """
                SELECT * FROM rejected_order WHERE broker = ?
                ORDER BY rejected_at DESC, id DESC LIMIT ?
                """)
            .param(broker)
            .param(MAX_ORDERS_PER_HISTORY_REQUEST)
            .query(OrderRepository::rejectedOrder)
            .list());
  }

  private Map<Long, List<OrderFill>> fillsClosedSince(String broker, OrderHistoryEntry oldest) {
    return jdbcClient
        .sql(
            """
            SELECT f.*
            FROM order_fill f
            JOIN order_history h ON h.exchange_order_id = f.exchange_order_id
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
                OrderFillRow::exchangeOrderId,
                Collectors.mapping(OrderFillRow::fill, Collectors.toList())));
  }

  private void insertFill(long exchangeOrderId, OrderFill fill) {
    jdbcClient
        .sql(
            """
            INSERT INTO order_fill (exchange_order_id, trade_id, price, quantity, commission,
                                    commission_asset)
            VALUES (?, ?, ?, ?, ?, ?)
            """)
        .param(exchangeOrderId)
        .param(fill.tradeId())
        .param(fill.price())
        .param(fill.quantity())
        .param(fill.commission())
        .param(fill.commissionAsset())
        .update();
  }

  private static OpenOrder openOrder(ResultSet row, int rowNumber) throws SQLException {
    return new OpenOrder(
        row.getLong("exchange_order_id"),
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
        row.getLong("exchange_order_id"),
        row.getString("client_order_id"),
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
        order.exchangeOrderId(),
        order.clientOrderId(),
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
        row.getString("broker"),
        row.getString("ticker"),
        OrderSide.valueOf(row.getString("side")),
        row.getString("order_type"),
        row.getBigDecimal("amount"),
        OrderAmountType.valueOf(row.getString("amount_type")),
        row.getInt("error_code"),
        row.getString("error_message"),
        instant(row, "rejected_at"));
  }

  private static OrderFillRow orderFillRow(ResultSet row, int rowNumber) throws SQLException {
    return new OrderFillRow(
        row.getLong("exchange_order_id"),
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
