package com.tihomir.t212.tradingbot.bot.live;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.order.AccountView;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.gateway.ExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.gateway.VirtualTradingGateway;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.OrderFill;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import com.tihomir.t212.tradingbot.position.PositionStore;
import com.tihomir.t212.tradingbot.position.PositionTracker;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One ticker's own money on the shared exchange account. Its cash starts at the ticker's budget and
 * changes only with this bot's orders, and is never more than the account's free USDT. The position
 * is only what this bot bought, kept by its {@link PositionTracker}, so it never sells coins the
 * account held before.
 */
final class LiveTradingAccount implements AccountView {

  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;
  private static final int AMOUNT_SCALE = 8;
  private static final Logger log = LoggerFactory.getLogger(LiveTradingAccount.class);

  private final String ticker;
  private final ExchangeTradingGateway gateway;
  private final PositionTracker positionTracker;
  private final PositionStore positionStore;
  private BigDecimal ownCash;

  LiveTradingAccount(
      String ticker,
      Broker broker,
      PositionTracker positionTracker,
      PositionStore positionStore,
      BigDecimal budget) {
    this.ticker = ticker;
    this.gateway = broker.tradingGateway();
    this.positionTracker = positionTracker;
    this.positionStore = positionStore;
    this.ownCash = budget;
  }

  @Override
  public String ticker() {
    return ticker;
  }

  /** The bot's own cash, but never more than the account really has free. */
  @Override
  public synchronized BigDecimal cash() {
    return ownCash.min(freeCash(gateway));
  }

  @Override
  public BigDecimal baseQuantity() {
    return positionTracker.quantity();
  }

  @Override
  public Optional<BigDecimal> entryPrice() {
    return positionTracker.entryPrice();
  }

  /**
   * Moves the order's USDT in or out of the bot's cash, applies it to the position and stores the
   * change. The order did happen: a failed write is logged and the position stays in memory.
   */
  synchronized void recordExecutedOrder(ExecutedOrder order) {
    BigDecimal quoteFee = quoteFee(order);
    if (order.side() == OrderSide.BUY) {
      ownCash = ownCash.subtract(order.quoteAmount()).subtract(quoteFee);
    } else {
      ownCash = ownCash.add(order.quoteAmount()).subtract(quoteFee);
    }
    positionTracker.recordExecutedOrder(order).ifPresent(write -> savePosition(order, write));
  }

  void updateExcursions(BigDecimal price) {
    positionTracker.updateExcursions(price);
  }

  void startRun(long runId) {
    positionTracker.startRun(runId);
  }

  /** The bot's own money: its cash plus its coins at this price. */
  synchronized BigDecimal portfolioValue(BigDecimal price) {
    return ownCash
        .add(positionTracker.quantity().multiply(price))
        .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
  }

  synchronized BigDecimal ownCash() {
    return ownCash;
  }

  private void savePosition(ExecutedOrder order, PositionWrite write) {
    try {
      positionStore.savePosition(write);
    } catch (RuntimeException e) {
      log.error(
          "{}: the position change of order {} could not be stored",
          ticker,
          order.exchangeOrderId(),
          e);
    }
  }

  private static BigDecimal quoteFee(ExecutedOrder order) {
    return order.fills().stream()
        .filter(fill -> QUOTE_ASSET.equals(fill.feeCurrency()))
        .map(OrderFill::feeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal freeCash(ExchangeTradingGateway gateway) {
    return gateway.accountBalances().balances().stream()
        .filter(balance -> balance.asset().equals(QUOTE_ASSET))
        .map(AssetBalance::free)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }
}
