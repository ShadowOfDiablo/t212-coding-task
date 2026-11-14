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
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Open, executed and rejected orders all live in the {@code orders} table, told apart by status: a
 * rejected order has an error and no exchange order id.
 */
@Repository
public class OrderRepository implements OrderStore {

  private static final int MAX_ORDERS_PER_HISTORY_REQUEST = 100;
  private static final String REJECTED = "REJECTED";
  private static final SqlParameterValue NO_EXCHANGE_ORDER_ID =
      new SqlParameterValue(Types.BIGINT, null);
  private static final SqlParameterValue NOT_EXECUTED = new SqlParameterValue(Types.NUMERIC, null);
  private static final SqlParameterValue NO_ERROR_CODE = new SqlParameterValue(Types.INTEGER, null);
  private static final SqlParameterValue NO_ERROR_MESSAGE =
      new SqlParameterValue(Types.VARCHAR, null);

  private static final String SELECT_ORDERS =
      """
      SELECT o.*, b.name AS broker, t.symbol AS ticker
      FROM orders o
      JOIN ticker t ON t.id = o.ticker_id
      JOIN brokers b ON b.broker_id = t.broker_id
      """;

  /** Executed orders: no error and no longer open. */
  private static final String HISTORY_FILTER =
      """
      WHERE b.name = ? AND o.error_code IS NULL
        AND o.status NOT IN (CAST(? AS order_status), CAST(? AS order_status))
      ORDER BY o.updated_at DESC, o.id DESC LIMIT ?
      """;

  private static final String INSERT_ORDER =
      """
      INSERT INTO orders (ticker_id, exchange_order_id, side, order_type, amount_type, status,
                          amount, executed_quantity, quote_amount, average_price, error_code,
                          error_message, created_at, updated_at)
      VALUES (?, ?, CAST(? AS order_side), CAST(? AS order_type), CAST(? AS order_amount_type),
              CAST(? AS order_status), ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private final JdbcClient jdbcClient;
  private final CandleRepository candleRepository;

  public OrderRepository(JdbcClient jdbcClient, CandleRepository candleRepository) {
    this.jdbcClient = jdbcClient;
    this.candleRepository = candleRepository;
  }

  /** An order already stored is left as it is. */
  @Override
  @Transactional
  public void insertExecutedOrder(OrderHistoryEntry order) {
    long tickerId = candleRepository.insertTicker(order.broker(), order.ticker());
    long alreadyStored =
        jdbcClient
            .sql("SELECT COUNT(*) FROM orders WHERE ticker_id = ? AND exchange_order_id = ?")
            .param(tickerId)
            .param(order.exchangeOrderId())
            .query(Long.class)
            .single();
    if (alreadyStored > 0) {
      return;
    }
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(INSERT_ORDER)
        .param(tickerId)
        .param(order.exchangeOrderId())
        .param(order.side().name())
        .param(order.orderType())
        .param(order.amountType().name())
        .param(order.finalStatus())
        .param(order.amount())
        .param(order.executedQuantity())
        .param(order.quoteAmount())
        .param(order.averagePrice())
        .param(NO_ERROR_CODE)
        .param(NO_ERROR_MESSAGE)
        .param(timestamp(order.createdAt()))
        .param(timestamp(order.closedAt()))
        .update(keyHolder, "id");
    Long orderId = keyHolder.getKeyAs(Long.class);
    if (orderId == null) {
      throw new IllegalStateException("The database returned no id for an order");
    }
    order.fills().forEach(fill -> insertFill(orderId, fill));
  }

  @Override
  public void insertRejectedOrder(RejectedOrder order) {
    long tickerId = candleRepository.insertTicker(order.broker(), order.ticker());
    OffsetDateTime rejectedAt = timestamp(order.rejectedAt());
    jdbcClient
        .sql(INSERT_ORDER)
        .param(tickerId)
        .param(NO_EXCHANGE_ORDER_ID)
        .param(order.side().name())
        .param(order.orderType())
        .param(order.amountType().name())
        .param(REJECTED)
        .param(order.amount())
        .param(NOT_EXECUTED)
        .param(NOT_EXECUTED)
        .param(NOT_EXECUTED)
        .param(order.errorCode())
        .param(order.errorMessage())
        .param(rejectedAt)
        .param(rejectedAt)
        .update();
  }

  @Override
  public OpenOrderList getOpenOrders(String broker) {
    return new OpenOrderList(
        jdbcClient
            .sql(
                SELECT_ORDERS
                    + "WHERE b.name = ?"
                    + " AND o.status IN (CAST(? AS order_status), CAST(? AS order_status))"
                    + " ORDER BY o.created_at")
            .param(broker)
            .param(OpenOrderStatus.NEW.name())
            .param(OpenOrderStatus.PARTIALLY_FILLED.name())
            .query(OrderRepository::openOrder)
            .list());
  }

  /** Reads the orders and their fills from one snapshot, so both see the same orders. */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OrderHistory getOrderHistory(String broker) {
    List<OrderHistoryRow> rows =
        jdbcClient
            .sql(SELECT_ORDERS + HISTORY_FILTER)
            .params(historyParams(broker))
            .query(OrderRepository::orderHistoryRow)
            .list();
    Map<Long, List<OrderFill>> fillsByOrder = historyFills(broker);
    return new OrderHistory(
        rows.stream()
            .map(row -> withFills(row.order(), fillsByOrder.getOrDefault(row.orderId(), List.of())))
            .toList());
  }

  @Override
  public RejectedOrderList getRejectedOrders(String broker) {
    return new RejectedOrderList(
        jdbcClient
            .sql(
                SELECT_ORDERS
                    + "WHERE b.name = ? AND o.status = CAST(? AS order_status)"
                    + " ORDER BY o.updated_at DESC, o.id DESC LIMIT ?")
            .param(broker)
            .param(REJECTED)
            .param(MAX_ORDERS_PER_HISTORY_REQUEST)
            .query(OrderRepository::rejectedOrder)
            .list());
  }

  /** The fills of the orders the history shows, read with the same filter. */
  private Map<Long, List<OrderFill>> historyFills(String broker) {
    return jdbcClient
        .sql(
            "SELECT f.* FROM order_fill f WHERE f.order_id IN (SELECT id FROM ("
                + SELECT_ORDERS
                + HISTORY_FILTER
                + ") history) ORDER BY f.trade_id")
        .params(historyParams(broker))
        .query(OrderRepository::orderFillRow)
        .list()
        .stream()
        .collect(
            Collectors.groupingBy(
                OrderFillRow::orderId,
                Collectors.mapping(OrderFillRow::fill, Collectors.toList())));
  }

  private static List<Object> historyParams(String broker) {
    return List.of(
        broker,
        OpenOrderStatus.NEW.name(),
        OpenOrderStatus.PARTIALLY_FILLED.name(),
        MAX_ORDERS_PER_HISTORY_REQUEST);
  }

  private void insertFill(long orderId, OrderFill fill) {
    jdbcClient
        .sql(
            """
            INSERT INTO order_fill (order_id, trade_id, price, quantity, fee_amount,
                                    fee_currency)
            VALUES (?, ?, ?, ?, ?, ?)
            """)
        .param(orderId)
        .param(fill.tradeId())
        .param(fill.price())
        .param(fill.quantity())
        .param(fill.feeAmount())
        .param(fill.feeCurrency())
        .update();
  }

  private static OpenOrder openOrder(ResultSet row, int rowNumber) throws SQLException {
    return new OpenOrder(
        row.getLong("exchange_order_id"),
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

  private static OrderHistoryRow orderHistoryRow(ResultSet row, int rowNumber) throws SQLException {
    return new OrderHistoryRow(
        row.getLong("id"),
        new OrderHistoryEntry(
            row.getLong("exchange_order_id"),
            row.getString("broker"),
            row.getString("ticker"),
            OrderSide.valueOf(row.getString("side")),
            row.getString("order_type"),
            row.getBigDecimal("amount"),
            OrderAmountType.valueOf(row.getString("amount_type")),
            row.getString("status"),
            row.getBigDecimal("executed_quantity"),
            row.getBigDecimal("quote_amount"),
            row.getBigDecimal("average_price"),
            instant(row, "created_at"),
            instant(row, "updated_at"),
            List.of()));
  }

  private static OrderHistoryEntry withFills(OrderHistoryEntry order, List<OrderFill> fills) {
    return new OrderHistoryEntry(
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
        row.getString("broker"),
        row.getString("ticker"),
        OrderSide.valueOf(row.getString("side")),
        row.getString("order_type"),
        row.getBigDecimal("amount"),
        OrderAmountType.valueOf(row.getString("amount_type")),
        row.getInt("error_code"),
        row.getString("error_message"),
        instant(row, "updated_at"));
  }

  private static OrderFillRow orderFillRow(ResultSet row, int rowNumber) throws SQLException {
    return new OrderFillRow(
        row.getLong("order_id"),
        new OrderFill(
            row.getLong("trade_id"),
            row.getBigDecimal("price"),
            row.getBigDecimal("quantity"),
            row.getBigDecimal("fee_amount"),
            row.getString("fee_currency")));
  }

  private static OffsetDateTime timestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    return row.getObject(column, OffsetDateTime.class).toInstant();
  }
}
