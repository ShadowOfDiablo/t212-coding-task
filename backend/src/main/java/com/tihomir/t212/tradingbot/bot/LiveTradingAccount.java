package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.order.AccountView;
import com.tihomir.t212.tradingbot.order.AssetBalance;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.VirtualTradingGateway;
import com.tihomir.t212.tradingbot.position.PositionStore;
import com.tihomir.t212.tradingbot.position.PositionTracker;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The live bot's view of the exchange account. Cash is the account's free quote balance; the
 * position is only what this bot bought, kept by its {@link PositionTracker}, so it never sells
 * coins the account held before.
 */
final class LiveTradingAccount implements AccountView {

  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;
  private static final int AMOUNT_SCALE = 8;
  private static final Logger log = LoggerFactory.getLogger(LiveTradingAccount.class);

  private final String ticker;
  private final ExchangeTradingGateway gateway;
  private final PositionTracker positionTracker;
  private final PositionStore positionStore;

  LiveTradingAccount(
      String ticker,
      ExchangeTradingGateway gateway,
      PositionTracker positionTracker,
      PositionStore positionStore) {
    this.ticker = ticker;
    this.gateway = gateway;
    this.positionTracker = positionTracker;
    this.positionStore = positionStore;
  }

  @Override
  public String ticker() {
    return ticker;
  }

  @Override
  public BigDecimal cash() {
    return freeCash(gateway);
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
   * Applies the order to the position and stores the change. The order did happen: a failed write
   * is logged and the position stays in memory.
   */
  void recordExecutedOrder(ExecutedOrder order) {
    positionTracker.recordExecutedOrder(order).ifPresent(write -> savePosition(order, write));
  }

  void updateExcursions(BigDecimal price) {
    positionTracker.updateExcursions(price);
  }

  void startRun(long runId) {
    positionTracker.startRun(runId);
  }

  /** Free cash plus the bot's position at this price. */
  BigDecimal portfolioValue(BigDecimal price) {
    return cash()
        .add(baseQuantity().multiply(price))
        .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
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

  static BigDecimal freeCash(ExchangeTradingGateway gateway) {
    return gateway.accountBalances().balances().stream()
        .filter(balance -> balance.asset().equals(QUOTE_ASSET))
        .map(AssetBalance::free)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }
}
