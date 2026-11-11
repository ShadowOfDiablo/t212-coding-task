package com.tihomir.t212.tradingbot.order;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final BrokerRegistryService brokers;
  private final OrderStore orderStore;
  private final Clock clock;

  public OrderService(BrokerRegistryService brokers, OrderStore orderStore, Clock clock) {
    this.brokers = brokers;
    this.orderStore = orderStore;
    this.clock = clock;
  }

  public ExecutedOrder placeMarketOrder(String brokerName, MarketOrderRequest request) {
    Broker broker = broker(brokerName);
    if (!broker.tickers().contains(request.ticker())) {
      throw new TickerNotTradedException(brokerName, request.ticker());
    }
    Instant createdAt = clock.instant();
    ExecutedOrder order;
    try {
      order = broker.tradingGateway().placeMarketOrder(request);
    } catch (ExchangeRejectedRequestException e) {
      recordRejectedOrder(RejectedOrder.of(brokerName, request, e, clock.instant()));
      throw e;
    }
    recordExecutedOrder(OrderHistoryEntry.of(brokerName, request, order, createdAt));
    log.info(
        "{}: {} {} {} executed {} for {} ({}), exchange order id {}",
        brokerName,
        order.side(),
        order.ticker(),
        order.exchangeStatus(),
        order.executedQuantity(),
        order.quoteAmount(),
        order.averagePrice(),
        order.exchangeOrderId());
    return order;
  }

  public AccountBalanceList getAccountBalances(String brokerName) {
    return new AccountBalanceList(broker(brokerName).tradingGateway().accountBalances().balances());
  }

  public OpenOrderList getOpenOrders(String brokerName) {
    broker(brokerName);
    return orderStore.getOpenOrders(brokerName);
  }

  public OrderHistory getOrderHistory(String brokerName) {
    broker(brokerName);
    return orderStore.getOrderHistory(brokerName);
  }

  public RejectedOrderList getRejectedOrders(String brokerName) {
    broker(brokerName);
    return orderStore.getRejectedOrders(brokerName);
  }

  private void recordExecutedOrder(OrderHistoryEntry order) {
    try {
      orderStore.insertExecutedOrder(order);
    } catch (DataAccessException e) {
      log.error(
          "Order {} was executed but could not be stored in the order history",
          order.exchangeOrderId(),
          e);
    }
  }

  private void recordRejectedOrder(RejectedOrder order) {
    try {
      orderStore.insertRejectedOrder(order);
    } catch (DataAccessException e) {
      log.error(
          "A {} {} order rejected with {} could not be stored",
          order.side(),
          order.ticker(),
          order.errorCode(),
          e);
    }
  }

  private Broker broker(String brokerName) {
    return brokers.get(brokerName).orElseThrow(() -> new BrokerNotFoundException(brokerName));
  }
}
