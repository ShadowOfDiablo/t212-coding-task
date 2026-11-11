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
    jdbcClient
        .sql(
            """
            INSERT INTO order_history (exchange_order_id, client_order_id, broker, ticker, side,
                                       order_type, amount, amount_type, final_status,
                                       executed_quantity, quote_amount, average_price,
                                       created_at, closed_at)
            VALUES (:exchangeOrderId, :clientOrderId, :broker, :ticker, :side, :orderType,
                    :amount, :amountType, :finalStatus, :executedQuantity, :quoteAmount,
                    :averagePrice, :createdAt, :closedAt)
            ON CONFLICT (exchange_order_id) DO NOTHING
            """)
        .param("exchangeOrderId", order.exchangeOrderId())
        .param("clientOrderId", order.clientOrderId())
        .param("broker", order.broker())
        .param("ticker", order.ticker())
        .param("side", order.side().name())
        .param("orderType", order.orderType())
        .param("amount", order.amount())
        .param("amountType", order.amountType().name())
        .param("finalStatus", order.finalStatus())
        .param("executedQuantity", order.executedQuantity())
        .param("quoteAmount", order.quoteAmount())
        .param("averagePrice", order.averagePrice())
        .param("createdAt", timestamp(order.createdAt()))
        .param("closedAt", timestamp(order.closedAt()))
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
            VALUES (:broker, :ticker, :side, :orderType, :amount, :amountType,
                    :errorCode, :errorMessage, :rejectedAt)
            """)
        .param("broker", order.broker())
        .param("ticker", order.ticker())
        .param("side", order.side().name())
        .param("orderType", order.orderType())
        .param("amount", order.amount())
        .param("amountType", order.amountType().name())
        .param("errorCode", order.errorCode())
        .param("errorMessage", order.errorMessage())
        .param("rejectedAt", timestamp(order.rejectedAt()))
        .update();
  }

  @Override
  public OpenOrderList getOpenOrders(String broker) {
    return new OpenOrderList(
        jdbcClient
            .sql("SELECT * FROM open_order WHERE broker = :broker ORDER BY created_at")
            .param("broker", broker)
            .query(OrderRepository::openOrder)
            .list());
  }

  @Override
  public OrderHistory getOrderHistory(String broker) {
    Map<Long, List<OrderFill>> fillsByOrder =
        jdbcClient
            .sql(
                """
                SELECT f.* FROM order_fill f
                JOIN (SELECT exchange_order_id FROM order_history WHERE broker = :broker
                      ORDER BY closed_at DESC LIMIT :maxOrders) h
                  ON h.exchange_order_id = f.exchange_order_id
                ORDER BY f.trade_id
                """)
            .param("broker", broker)
            .param("maxOrders", MAX_ORDERS_PER_HISTORY_REQUEST)
            .query(OrderRepository::orderFillRow)
            .list()
            .stream()
            .collect(
                Collectors.groupingBy(
                    OrderFillRow::exchangeOrderId,
                    Collectors.mapping(OrderFillRow::fill, Collectors.toList())));
    return new OrderHistory(
        jdbcClient
            .sql(
                """
                SELECT * FROM order_history WHERE broker = :broker
                ORDER BY closed_at DESC LIMIT :maxOrders
                """)
            .param("broker", broker)
            .param("maxOrders", MAX_ORDERS_PER_HISTORY_REQUEST)
            .query((row, rowNumber) -> orderHistoryEntry(row, fillsByOrder))
            .list());
  }

  @Override
  public RejectedOrderList getRejectedOrders(String broker) {
    return new RejectedOrderList(
        jdbcClient
            .sql(
                """
                SELECT * FROM rejected_order WHERE broker = :broker
                ORDER BY rejected_at DESC, id DESC LIMIT :maxOrders
                """)
            .param("broker", broker)
            .param("maxOrders", MAX_ORDERS_PER_HISTORY_REQUEST)
            .query(OrderRepository::rejectedOrder)
            .list());
  }

  private void insertFill(long exchangeOrderId, OrderFill fill) {
    jdbcClient
        .sql(
            """
            INSERT INTO order_fill (exchange_order_id, trade_id, price, quantity, commission,
                                    commission_asset)
            VALUES (:exchangeOrderId, :tradeId, :price, :quantity, :commission, :commissionAsset)
            ON CONFLICT (exchange_order_id, trade_id) DO NOTHING
            """)
        .param("exchangeOrderId", exchangeOrderId)
        .param("tradeId", fill.tradeId())
        .param("price", fill.price())
        .param("quantity", fill.quantity())
        .param("commission", fill.commission())
        .param("commissionAsset", fill.commissionAsset())
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

  private static OrderHistoryEntry orderHistoryEntry(
      ResultSet row, Map<Long, List<OrderFill>> fillsByOrder) throws SQLException {
    long exchangeOrderId = row.getLong("exchange_order_id");
    return new OrderHistoryEntry(
        exchangeOrderId,
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
        fillsByOrder.getOrDefault(exchangeOrderId, List.of()));
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
