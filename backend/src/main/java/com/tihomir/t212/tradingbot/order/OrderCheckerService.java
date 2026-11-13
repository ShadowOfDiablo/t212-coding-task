package com.tihomir.t212.tradingbot.order;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
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
  static final BigDecimal MIN_ORDER_VALUE = new BigDecimal("5");

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

  /**
   * Turns a BUY or SELL signal into an order, or refuses it.
   *
   * <ul>
   *   <li>BUY spends {@code orderSizePercent} of the cash. With a risk per trade, the amount is
   *       capped so that a fall to the stop loss costs at most that share of the portfolio.
   *   <li>SELL sells the whole position.
   * </ul>
   *
   * @param price the price the order is expected to fill at
   */
  public OrderCheck checkOrder(
      TradingSignal signal, AccountView account, BigDecimal price, AlgorithmSettings settings) {
    return switch (signal) {
      case BUY -> checkBuy(account, price, settings);
      case SELL -> checkSell(account, price);
      case HOLD -> throw new IllegalArgumentException("HOLD is not an order");
    };
  }

  /**
   * Whether the position must be sold because the price fell to the stop loss. False when no stop
   * loss is set or nothing is held.
   */
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

  private OrderCheck checkBuy(AccountView account, BigDecimal price, AlgorithmSettings settings) {
    if (account.cash().signum() <= 0) {
      return OrderCheck.refused(RejectionReason.INSUFFICIENT_FUNDS);
    }
    BigDecimal amount =
        riskCappedAmount(
            account, price, settings, percentOf(account.cash(), settings.orderSizePercent()));
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

  /** Spending x with a stop s% below loses x * s%; that loss may be at most risk% of the value. */
  private static BigDecimal riskCappedAmount(
      AccountView account, BigDecimal price, AlgorithmSettings settings, BigDecimal amount) {
    if (settings.riskPerTradePercent().isEmpty() || settings.stopLossPercent().isEmpty()) {
      return amount;
    }
    BigDecimal portfolioValue = account.cash().add(account.baseQuantity().multiply(price));
    BigDecimal maxLoss = percentOf(portfolioValue, settings.riskPerTradePercent().get());
    BigDecimal maxAmount =
        maxLoss
            .multiply(PERCENT)
            .divide(settings.stopLossPercent().get(), AMOUNT_SCALE, RoundingMode.DOWN);
    return amount.min(maxAmount);
  }

  private static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
    return value.multiply(percent).divide(PERCENT, AMOUNT_SCALE, RoundingMode.DOWN);
  }
}
