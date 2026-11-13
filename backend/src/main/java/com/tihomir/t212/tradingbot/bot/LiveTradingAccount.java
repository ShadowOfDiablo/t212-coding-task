package com.tihomir.t212.tradingbot.bot;

import com.tihomir.t212.tradingbot.order.AccountView;
import com.tihomir.t212.tradingbot.order.AssetBalance;
import com.tihomir.t212.tradingbot.order.ExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.OrderSide;
import com.tihomir.t212.tradingbot.order.VirtualTradingGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The live bot's view of the exchange account. Cash is the account's free quote balance; the
 * position is only what this bot bought, so it never sells coins the account held before.
 */
final class LiveTradingAccount implements AccountView {

  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;
  private static final int AMOUNT_SCALE = 8;

  private final String ticker;
  private final String baseAsset;
  private final ExchangeTradingGateway gateway;
  private BigDecimal positionQuantity = BigDecimal.ZERO;
  private BigDecimal positionCost = BigDecimal.ZERO;

  LiveTradingAccount(String ticker, ExchangeTradingGateway gateway) {
    if (!ticker.endsWith(QUOTE_ASSET) || ticker.equals(QUOTE_ASSET)) {
      throw new IllegalArgumentException("The live bot trades " + QUOTE_ASSET + " tickers only");
    }
    this.ticker = ticker;
    this.baseAsset = ticker.substring(0, ticker.length() - QUOTE_ASSET.length());
    this.gateway = gateway;
  }

  @Override
  public String ticker() {
    return ticker;
  }

  @Override
  public BigDecimal cash() {
    return gateway.accountBalances().balances().stream()
        .filter(balance -> balance.asset().equals(QUOTE_ASSET))
        .map(AssetBalance::free)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  @Override
  public synchronized BigDecimal baseQuantity() {
    return positionQuantity;
  }

  @Override
  public synchronized Optional<BigDecimal> entryPrice() {
    if (positionQuantity.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(positionCost.divide(positionQuantity, AMOUNT_SCALE, RoundingMode.HALF_EVEN));
  }

  /**
   * A sell always sells the whole position, so it closes it. What the exchange's lot step leaves
   * behind stays in the account but is no longer the bot's position, so it can't trigger the stop
   * loss again.
   */
  synchronized void recordExecutedOrder(ExecutedOrder order) {
    if (order.side() == OrderSide.SELL) {
      positionQuantity = BigDecimal.ZERO;
      positionCost = BigDecimal.ZERO;
      return;
    }
    positionQuantity = positionQuantity.add(receivedBaseQuantity(order));
    positionCost = positionCost.add(order.quoteAmount());
  }

  BigDecimal portfolioValue(BigDecimal price) {
    return cash()
        .add(baseQuantity().multiply(price))
        .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
  }

  /** A buy's fee may be taken from the coins bought. */
  private BigDecimal receivedBaseQuantity(ExecutedOrder order) {
    BigDecimal baseFees =
        order.fills().stream()
            .filter(fill -> baseAsset.equals(fill.commissionAsset()))
            .map(OrderFill::commission)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return order.executedQuantity().subtract(baseFees);
  }
}
