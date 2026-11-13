package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OrderCheck;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.OrderService;
import com.tihomir.t212.tradingbot.position.PositionStore;
import com.tihomir.t212.tradingbot.position.PositionWrite;
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
  private final PositionStore positionStore;

  LiveOrderPlacer(
      String brokerName,
      LiveTradingAccount account,
      OrderCheckerService orderChecker,
      OrderService orderService,
      PositionStore positionStore) {
    this.brokerName = brokerName;
    this.account = account;
    this.orderChecker = orderChecker;
    this.orderService = orderService;
    this.positionStore = positionStore;
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
      account.recordExecutedOrder(order).ifPresent(write -> savePosition(order, write));
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

  /** The order did happen: a failed write is logged and the position stays in memory. */
  private void savePosition(ExecutedOrder order, PositionWrite write) {
    try {
      positionStore.savePosition(write);
    } catch (RuntimeException e) {
      log.error(
          "{} {}: the position change of order {} could not be stored",
          brokerName,
          account.ticker(),
          order.exchangeOrderId(),
          e);
    }
  }
}
