package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.order.AccountView;
import com.tihomir.t212.tradingbot.order.AssetBalance;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.VirtualTradingGateway;
import com.tihomir.t212.tradingbot.position.PositionTracker;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The live bot's view of the exchange account. Cash is the account's free quote balance; the
 * position is only what this bot bought, kept by its {@link PositionTracker}, so it never sells
 * coins the account held before.
 */
final class LiveTradingAccount implements AccountView {

  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;
  private static final int AMOUNT_SCALE = 8;

  private final String ticker;
  private final ExchangeTradingGateway gateway;
  private final PositionTracker positionTracker;

  LiveTradingAccount(
      String ticker, ExchangeTradingGateway gateway, PositionTracker positionTracker) {
    this.ticker = ticker;
    this.gateway = gateway;
    this.positionTracker = positionTracker;
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

  /** What changed in the position, to be stored; empty when nothing changed. */
  Optional<PositionWrite> recordExecutedOrder(ExecutedOrder order) {
    return positionTracker.recordExecutedOrder(order);
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

  static BigDecimal freeCash(ExchangeTradingGateway gateway) {
    return gateway.accountBalances().balances().stream()
        .filter(balance -> balance.asset().equals(QUOTE_ASSET))
        .map(AssetBalance::free)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }
}
