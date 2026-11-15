package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.order.OpenOrder;
import com.tihomir.t212.tradingbot.order.OpenOrderList;
import com.tihomir.t212.tradingbot.order.OpenOrderStatus;
import com.tihomir.t212.tradingbot.order.OrderAmountType;
import com.tihomir.t212.tradingbot.order.OrderFee;
import com.tihomir.t212.tradingbot.order.OrderHistory;
import com.tihomir.t212.tradingbot.order.OrderHistoryEntry;
import com.tihomir.t212.tradingbot.order.OrderSide;
import com.tihomir.t212.tradingbot.order.OrderStore;
import com.tihomir.t212.tradingbot.order.RejectedOrder;
import com.tihomir.t212.tradingbot.order.RejectedOrderList;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
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
  private static final SqlParameterValue NO_FEE_AMOUNT = new SqlParameterValue(Types.NUMERIC, null);
  private static final SqlParameterValue NO_FEE_CURRENCY =
      new SqlParameterValue(Types.VARCHAR, null);

  private static final String SELECT_ORDERS =
      """
      SELECT o.*, b.name AS broker, t.symbol AS ticker
      FROM orders o
      JOIN ticker t ON t.id = o.ticker_id
      JOIN brokers b ON b.broker_id = t.broker_id
      """;

  private static final String INSERT_ORDER =
      """
      INSERT INTO orders (ticker_id, exchange_order_id, side, order_type, amount_type, status,
                          amount, executed_quantity, quote_amount, average_price, error_code,
                          error_message, fee_amount, fee_currency, created_at, updated_at)
      VALUES (?, ?, CAST(? AS order_side), CAST(? AS order_type), CAST(? AS order_amount_type),
              CAST(? AS order_status), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        .param(feeAmount(order.fee()))
        .param(feeCurrency(order.fee()))
        .param(timestamp(order.createdAt()))
        .param(timestamp(order.closedAt()))
        .update();
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
        .param(NO_FEE_AMOUNT)
        .param(NO_FEE_CURRENCY)
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

  /** Executed orders: no error and no longer open, newest first. */
  @Override
  public OrderHistory getOrderHistory(String broker) {
    return new OrderHistory(
        jdbcClient
            .sql(
                SELECT_ORDERS
                    + "WHERE b.name = ? AND o.error_code IS NULL"
                    + " AND o.status NOT IN (CAST(? AS order_status), CAST(? AS order_status))"
                    + " ORDER BY o.updated_at DESC, o.id DESC LIMIT ?")
            .param(broker)
            .param(OpenOrderStatus.NEW.name())
            .param(OpenOrderStatus.PARTIALLY_FILLED.name())
            .param(MAX_ORDERS_PER_HISTORY_REQUEST)
            .query(OrderRepository::orderHistoryEntry)
            .list());
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

  private static OrderHistoryEntry orderHistoryEntry(ResultSet row, int rowNumber)
      throws SQLException {
    return new OrderHistoryEntry(
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
        fee(row));
  }

  private static Optional<OrderFee> fee(ResultSet row) throws SQLException {
    BigDecimal amount = row.getBigDecimal("fee_amount");
    if (amount == null) {
      return Optional.empty();
    }
    return Optional.of(new OrderFee(amount, row.getString("fee_currency")));
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

  private static SqlParameterValue feeAmount(Optional<OrderFee> fee) {
    return new SqlParameterValue(Types.NUMERIC, fee.map(OrderFee::amount).orElse(null));
  }

  private static SqlParameterValue feeCurrency(Optional<OrderFee> fee) {
    return new SqlParameterValue(Types.VARCHAR, fee.map(OrderFee::currency).orElse(null));
  }

  private static OffsetDateTime timestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    return row.getObject(column, OffsetDateTime.class).toInstant();
  }
}
