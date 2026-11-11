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
            VALUES (:clientOrderId, :broker, :ticker, :side, :orderType, :amount,
                    :amountType, :status, :createdAt, :updatedAt)
            """)
        .param("clientOrderId", order.clientOrderId())
        .param("broker", order.broker())
        .param("ticker", order.ticker())
        .param("side", order.side().name())
        .param("orderType", order.orderType())
        .param("amount", order.amount())
        .param("amountType", order.amountType().name())
        .param("status", order.status().name())
        .param("createdAt", timestamp(order.createdAt()))
        .param("updatedAt", timestamp(order.updatedAt()))
        .update();
  }

  @Override
  public void updateOpenOrderStatus(
      String clientOrderId, OpenOrderStatus status, Instant updatedAt) {
    jdbcClient
        .sql(
            """
            UPDATE open_order SET status = :status, updated_at = :updatedAt
            WHERE client_order_id = :clientOrderId
            """)
        .param("status", status.name())
        .param("updatedAt", timestamp(updatedAt))
        .param("clientOrderId", clientOrderId)
        .update();
  }

  @Override
  public void deleteOpenOrder(String clientOrderId) {
    jdbcClient
        .sql("DELETE FROM open_order WHERE client_order_id = :clientOrderId")
        .param("clientOrderId", clientOrderId)
        .update();
  }

  @Override
  @Transactional
  public void moveExecutedOrderToHistory(ExecutedOrder order) {
    jdbcClient
        .sql(
            """
            INSERT INTO order_history (client_order_id, exchange_order_id, broker, ticker, side,
                                       order_type, amount, amount_type, final_status,
                                       executed_quantity, quote_amount, average_price,
                                       created_at, closed_at)
            SELECT client_order_id, :exchangeOrderId, broker, ticker, side, order_type, amount,
                   amount_type, :finalStatus, :executedQuantity, :quoteAmount, :averagePrice,
                   created_at, :closedAt
            FROM open_order WHERE client_order_id = :clientOrderId
            ON CONFLICT (client_order_id) DO NOTHING
            """)
        .param("exchangeOrderId", order.exchangeOrderId())
        .param("finalStatus", order.exchangeStatus())
        .param("executedQuantity", order.executedQuantity())
        .param("quoteAmount", order.quoteAmount())
        .param("averagePrice", order.averagePrice())
        .param("closedAt", timestamp(order.transactTime()))
        .param("clientOrderId", order.clientOrderId())
        .update();
    order.fills().forEach(fill -> insertFill(order.clientOrderId(), fill));
    deleteOpenOrder(order.clientOrderId());
  }

  @Override
  @Transactional
  public void moveOrderToRejectedOrders(
      String clientOrderId, ExchangeRejectedRequestException rejection, Instant rejectedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO rejected_order (client_order_id, broker, ticker, side, order_type, amount,
                                        amount_type, error_code, error_message, created_at,
                                        rejected_at)
            SELECT client_order_id, broker, ticker, side, order_type, amount, amount_type,
                   :errorCode, :errorMessage, created_at, :rejectedAt
            FROM open_order WHERE client_order_id = :clientOrderId
            ON CONFLICT (client_order_id) DO NOTHING
            """)
        .param("errorCode", rejection.exchangeErrorCode())
        .param("errorMessage", rejection.getMessage())
        .param("rejectedAt", timestamp(rejectedAt))
        .param("clientOrderId", clientOrderId)
        .update();
    deleteOpenOrder(clientOrderId);
  }

  @Override
  public List<OpenOrder> getOpenOrders(String broker) {
    return jdbcClient
        .sql("SELECT * FROM open_order WHERE broker = :broker ORDER BY created_at")
        .param("broker", broker)
        .query(OrderRepository::openOrder)
        .list();
  }

  @Override
  public List<OrderHistoryEntry> getOrderHistory(String broker) {
    Map<String, List<OrderFill>> fillsByOrder =
        jdbcClient
            .sql(
                """
                SELECT f.* FROM order_fill f
                JOIN (SELECT client_order_id FROM order_history WHERE broker = :broker
                      ORDER BY closed_at DESC LIMIT :maxOrders) h
                  ON h.client_order_id = f.client_order_id
                ORDER BY f.trade_id
                """)
            .param("broker", broker)
            .param("maxOrders", MAX_ORDERS_PER_HISTORY_REQUEST)
            .query(OrderRepository::orderFillRow)
            .list()
            .stream()
            .collect(
                Collectors.groupingBy(
                    OrderFillRow::clientOrderId,
                    Collectors.mapping(OrderFillRow::fill, Collectors.toList())));
    return jdbcClient
        .sql(
            """
            SELECT * FROM order_history WHERE broker = :broker
            ORDER BY closed_at DESC LIMIT :maxOrders
            """)
        .param("broker", broker)
        .param("maxOrders", MAX_ORDERS_PER_HISTORY_REQUEST)
        .query((row, rowNumber) -> orderHistoryEntry(row, fillsByOrder))
        .list();
  }

  @Override
  public List<RejectedOrder> getRejectedOrders(String broker) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM rejected_order WHERE broker = :broker
            ORDER BY rejected_at DESC LIMIT :maxOrders
            """)
        .param("broker", broker)
        .param("maxOrders", MAX_ORDERS_PER_HISTORY_REQUEST)
        .query(OrderRepository::rejectedOrder)
        .list();
  }

  private void insertFill(String clientOrderId, OrderFill fill) {
    jdbcClient
        .sql(
            """
            INSERT INTO order_fill (client_order_id, trade_id, price, quantity, commission,
                                    commission_asset)
            VALUES (:clientOrderId, :tradeId, :price, :quantity, :commission, :commissionAsset)
            ON CONFLICT (client_order_id, trade_id) DO NOTHING
            """)
        .param("clientOrderId", clientOrderId)
        .param("tradeId", fill.tradeId())
        .param("price", fill.price())
        .param("quantity", fill.quantity())
        .param("commission", fill.commission())
        .param("commissionAsset", fill.commissionAsset())
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

  private static OrderHistoryEntry orderHistoryEntry(
      ResultSet row, Map<String, List<OrderFill>> fillsByOrder) throws SQLException {
    String clientOrderId = row.getString("client_order_id");
    return new OrderHistoryEntry(
        clientOrderId,
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
        fillsByOrder.getOrDefault(clientOrderId, List.of()));
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
