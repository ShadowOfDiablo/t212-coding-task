package com.tihomir.t212.tradingbot.order;

import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.OrderAmountType;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checks every order before it is placed, in training and in trading, and places the live bot's
 * approved orders on the exchange. An optional setting that is empty skips its check.
 */
@Service
public class OrderCheckerService {

  /** Binance refuses market orders worth less than about 5 USDT. */
  public static final BigDecimal MIN_ORDER_VALUE = new BigDecimal("5");

  private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
  private static final int AMOUNT_SCALE = 8;

  private static final Logger log = LoggerFactory.getLogger(OrderCheckerService.class);

  private final OrderService orderService;

  public OrderCheckerService(OrderService orderService) {
    this.orderService = orderService;
  }

  /**
   * Checks a live signal and places the order on the exchange only when the checks approve it.
   *
   * @return the executed order; empty when the checks refused it or the exchange rejected it
   */
  public Optional<ExecutedOrder> placeCheckedOrder(
      String brokerName,
      TradingSignal signal,
      AccountView account,
      BigDecimal price,
      AlgorithmSettings settings) {
    OrderCheck check = checkOrder(signal, account, price, settings);
    if (!check.isApproved()) {
      log.info(
          "{} {}: live {} refused: {}",
          brokerName,
          account.ticker(),
          signal,
          check.refusal().orElseThrow());
      return Optional.empty();
    }
    try {
      return Optional.of(orderService.placeMarketOrder(brokerName, check.order().orElseThrow()));
    } catch (ExchangeRejectedRequestException e) {
      log.warn(
          "{} {}: live {} rejected by the exchange: {}",
          brokerName,
          account.ticker(),
          signal,
          e.getMessage());
      return Optional.empty();
    }
  }

  public OrderCheck checkOrder(
      TradingSignal signal, AccountView account, BigDecimal price, AlgorithmSettings settings) {
    return switch (signal) {
      case BUY -> checkBuy(account, settings);
      case SELL -> checkSell(account, price);
      case HOLD -> throw new IllegalArgumentException("HOLD is not an order");
    };
  }

  public boolean isStopLossReached(
      AccountView account, BigDecimal lowestPrice, AlgorithmSettings settings) {
    return stopPrice(account, settings).map(stop -> lowestPrice.compareTo(stop) <= 0).orElse(false);
  }

  /** The stop loss price of the position held; empty without a stop loss or a position. */
  public Optional<BigDecimal> stopPrice(AccountView account, AlgorithmSettings settings) {
    return settings
        .stopLossPercent()
        .flatMap(stopLoss ->
            account.entryPrice().map(entry -> entry.subtract(percentOf(entry, stopLoss))));
  }

  public boolean isTakeProfitReached(
      AccountView account, BigDecimal highestPrice, AlgorithmSettings settings) {
    return takeProfitPrice(account, settings)
        .map(takeProfit -> highestPrice.compareTo(takeProfit) >= 0)
        .orElse(false);
  }

  /**
   * Entry price × (1 + take profit %) of the position held; empty without a take profit or a
   * position.
   */
  public Optional<BigDecimal> takeProfitPrice(AccountView account, AlgorithmSettings settings) {
    return settings
        .takeProfitPercent()
        .flatMap(takeProfit ->
            account.entryPrice().map(entry -> entry.add(percentOf(entry, takeProfit))));
  }

  private OrderCheck checkBuy(AccountView account, AlgorithmSettings settings) {
    if (account.cash().signum() <= 0) {
      return OrderCheck.refused(RejectionReason.INSUFFICIENT_FUNDS);
    }
    BigDecimal amount = percentOf(account.cash(), settings.orderSizePercent());
    if (amount.compareTo(MIN_ORDER_VALUE) < 0) {
      return OrderCheck.refused(RejectionReason.BELOW_MIN_ORDER_VALUE);
    }
    return OrderCheck.approved(
        new MarketOrderRequest(
            account.ticker(), OrderSide.BUY, amount, OrderAmountType.QUOTE_AMOUNT));
  }

  private OrderCheck checkSell(AccountView account, BigDecimal price) {
    BigDecimal quantity = account.baseQuantity();
    if (quantity.signum() <= 0) {
      return OrderCheck.refused(RejectionReason.NO_POSITION);
    }
    if (quantity.multiply(price).compareTo(MIN_ORDER_VALUE) < 0) {
      return OrderCheck.refused(RejectionReason.BELOW_MIN_ORDER_VALUE);
    }
    return OrderCheck.approved(
        new MarketOrderRequest(
            account.ticker(), OrderSide.SELL, quantity, OrderAmountType.BASE_QUANTITY));
  }

  private static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
    return value.multiply(percent).divide(PERCENT, AMOUNT_SCALE, RoundingMode.DOWN);
  }
}
