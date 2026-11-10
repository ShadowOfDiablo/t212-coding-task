package com.tihomir.t212.tradingbot.order;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final BrokerRegistryService brokers;

  public OrderService(BrokerRegistryService brokers) {
    this.brokers = brokers;
  }

  public ExecutedOrder placeMarketOrder(String brokerName, MarketOrderRequest request) {
    Broker broker = broker(brokerName);
    if (!broker.tickers().contains(request.ticker())) {
      throw new TickerNotTradedException(brokerName, request.ticker());
    }
    ExecutedOrder order = tradingGateway(broker).placeMarketOrder(request);
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
    return tradingGateway(broker(brokerName)).accountBalances();
  }

  private Broker broker(String brokerName) {
    return brokers.get(brokerName).orElseThrow(() -> new BrokerNotFoundException(brokerName));
  }

  private static ExchangeTradingGateway tradingGateway(Broker broker) {
    return broker
        .tradingGateway()
        .orElseThrow(() -> new TradingNotConfiguredException(broker.name()));
  }
}
