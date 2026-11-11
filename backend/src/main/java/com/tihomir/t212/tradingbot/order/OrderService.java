package com.tihomir.t212.tradingbot.order;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private static final String CLIENT_ORDER_ID_PREFIX = "tb-";

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
    String clientOrderId = newClientOrderId();
    orderStore.insertOpenOrder(
        OpenOrder.sending(clientOrderId, brokerName, request, clock.instant()));
    ExecutedOrder order = sendMarketOrder(broker, clientOrderId, request);
    recordExecutedOrder(order);
    log.info(
        "{}: {} {} {} executed {} for {} ({}), client order id {}",
        brokerName,
        order.side(),
        order.ticker(),
        order.exchangeStatus(),
        order.executedQuantity(),
        order.quoteAmount(),
        order.averagePrice(),
        order.clientOrderId());
    return order;
  }

  public List<AssetBalance> accountBalances(String brokerName) {
    return broker(brokerName).tradingGateway().accountBalances().balances();
  }

  public List<OpenOrder> openOrders(String brokerName) {
    broker(brokerName);
    return orderStore.openOrders(brokerName);
  }

  public List<OrderHistoryEntry> orderHistory(String brokerName) {
    broker(brokerName);
    return orderStore.orderHistory(brokerName);
  }

  private ExecutedOrder sendMarketOrder(
      Broker broker, String clientOrderId, MarketOrderRequest request) {
    try {
      return broker.tradingGateway().placeMarketOrder(clientOrderId, request);
    } catch (ExchangeRejectedRequestException e) {
      recordRejectedOrder(clientOrderId, e);
      throw e;
    } catch (TradingNotConfiguredException e) {
      forgetUnsentOrder(clientOrderId);
      throw e;
    } catch (RuntimeException e) {
      markStatusUnknown(clientOrderId);
      throw e;
    }
  }

  private void recordExecutedOrder(ExecutedOrder order) {
    try {
      orderStore.settleExecutedOrder(order);
    } catch (DataAccessException e) {
      log.error(
          "Order {} was executed but could not be moved to the order history; it stays open"
              + " until it is reconciled with the exchange",
          order.clientOrderId(),
          e);
    }
  }

  private void recordRejectedOrder(String clientOrderId, ExchangeRejectedRequestException e) {
    try {
      orderStore.settleRejectedOrder(clientOrderId, e, clock.instant());
    } catch (DataAccessException storeFailure) {
      log.error(
          "Order {} was rejected but could not be moved to the order history",
          clientOrderId,
          storeFailure);
    }
  }

  private void forgetUnsentOrder(String clientOrderId) {
    try {
      orderStore.deleteOpenOrder(clientOrderId);
    } catch (DataAccessException e) {
      log.error("Order {} was never sent but could not be removed", clientOrderId, e);
    }
  }

  private void markStatusUnknown(String clientOrderId) {
    try {
      orderStore.markOpenOrderStatus(clientOrderId, OpenOrderStatus.UNKNOWN, clock.instant());
    } catch (DataAccessException e) {
      log.error("Order {} has an unknown status that could not be recorded", clientOrderId, e);
    }
  }

  private Broker broker(String brokerName) {
    return brokers.get(brokerName).orElseThrow(() -> new BrokerNotFoundException(brokerName));
  }

  private static String newClientOrderId() {
    return CLIENT_ORDER_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
  }
}
