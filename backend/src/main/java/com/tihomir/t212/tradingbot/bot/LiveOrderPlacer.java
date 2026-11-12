package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OrderCheck;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.OrderService;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks the live bot's orders and sends the approved ones to the exchange. */
final class LiveOrderPlacer {

  private static final Logger log = LoggerFactory.getLogger(LiveOrderPlacer.class);

  private final String brokerName;
  private final LiveTradingAccount account;
  private final OrderCheckerService orderChecker;
  private final OrderService orderService;

  LiveOrderPlacer(
      String brokerName,
      LiveTradingAccount account,
      OrderCheckerService orderChecker,
      OrderService orderService) {
    this.brokerName = brokerName;
    this.account = account;
    this.orderChecker = orderChecker;
    this.orderService = orderService;
  }

  LiveTradingAccount account() {
    return account;
  }

  boolean isStopLossReached(BigDecimal price, AlgorithmSettings settings) {
    return orderChecker.isStopLossReached(account, price, settings);
  }

  /**
   * @return true when the order was executed; false when the checks refused it or the exchange
   *     rejected it
   */
  boolean placeCheckedOrder(TradingSignal signal, BigDecimal price, AlgorithmSettings settings) {
    OrderCheck check = orderChecker.checkOrder(signal, account, price, settings);
    if (!check.isApproved()) {
      log.info(
          "{} {}: live {} refused: {}",
          brokerName,
          account.ticker(),
          signal,
          check.refusal().orElseThrow());
      return false;
    }
    try {
      ExecutedOrder order = orderService.placeMarketOrder(brokerName, check.order().orElseThrow());
      account.recordExecutedOrder(order);
      return true;
    } catch (ExchangeRejectedRequestException e) {
      log.warn(
          "{} {}: live {} rejected by the exchange: {}",
          brokerName,
          account.ticker(),
          signal,
          e.getMessage());
      return false;
    }
  }
}
