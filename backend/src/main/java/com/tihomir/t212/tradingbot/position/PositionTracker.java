package com.tihomir.t212.tradingbot.position;

import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.VirtualTradingGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The bot's position on one ticker at average cost: a buy adds its coins and its cost, a sell takes
 * coins out at the average cost per coin. Fees paid in the base or quote asset are counted; fees in
 * any other asset are not.
 *
 * <p>When what is left is worth less than the minimum order value at the sell price, it can never
 * be sold: it stays in the account but leaves the position, which closes. That is what the
 * exchange's lot step leaves behind when the whole position is sold.
 */
public final class PositionTracker {

  private static final Logger log = LoggerFactory.getLogger(PositionTracker.class);
  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;
  private static final int AMOUNT_SCALE = 8;

  private final String broker;
  private final String ticker;
  private final String baseAsset;
  private BigDecimal heldQuantity = BigDecimal.ZERO;
  private BigDecimal heldCost = BigDecimal.ZERO;
  private Optional<Position> openPosition = Optional.empty();
  private Optional<Long> runId = Optional.empty();

  /**
   * @throws IllegalArgumentException unless the ticker is quoted in USDT
   */
  public PositionTracker(String broker, String ticker) {
    if (!ticker.endsWith(QUOTE_ASSET) || ticker.equals(QUOTE_ASSET)) {
      throw new IllegalArgumentException("Positions are kept for " + QUOTE_ASSET + " tickers only");
    }
    this.broker = broker;
    this.ticker = ticker;
    this.baseAsset = ticker.substring(0, ticker.length() - QUOTE_ASSET.length());
  }

  /** New positions belong to this run from now on. */
  public synchronized void startRun(long newRunId) {
    runId = Optional.of(newRunId);
  }

  public synchronized BigDecimal quantity() {
    return heldQuantity;
  }

  /** The average USDT paid per coin held, buy fees included. */
  public synchronized Optional<BigDecimal> entryPrice() {
    if (heldQuantity.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(heldCost.divide(heldQuantity, AMOUNT_SCALE, RoundingMode.HALF_EVEN));
  }

  public synchronized void updateExcursions(BigDecimal price) {
    if (openPosition.isPresent()) {
      openPosition = Optional.of(withExcursions(openPosition.get(), price));
    }
  }

  /**
   * Applies an executed order to the position.
   *
   * @return what changed, to be stored; empty when the order changed nothing, like a sell while no
   *     position is held
   */
  public synchronized Optional<PositionWrite> recordExecutedOrder(ExecutedOrder order) {
    return switch (order.side()) {
      case BUY -> recordBuy(order);
      case SELL -> recordSell(order);
    };
  }

  /** The open position as it is now, to be stored when a run ends; empty while flat. */
  public synchronized Optional<PositionWrite> currentWrite() {
    return openPosition.map(PositionWrite::new);
  }

  private Optional<PositionWrite> recordBuy(ExecutedOrder order) {
    BigDecimal received = order.executedQuantity().subtract(fees(order, baseAsset));
    BigDecimal spent = order.quoteAmount().add(fees(order, QUOTE_ASSET));
    if (received.signum() <= 0) {
      log.warn("{} {}: buy {} received no coins", broker, ticker, order.exchangeOrderId());
      return Optional.empty();
    }
    heldQuantity = heldQuantity.add(received);
    heldCost = heldCost.add(spent);
    Position position =
        openPosition.map(open -> withCost(open, spent)).orElse(opened(order.transactTime(), spent));
    openPosition = Optional.of(position);
    return Optional.of(new PositionWrite(position));
  }

  private Optional<PositionWrite> recordSell(ExecutedOrder order) {
    if (openPosition.isEmpty()) {
      log.warn("{} {}: sell {} while no position is held", broker, ticker, order.exchangeOrderId());
      return Optional.empty();
    }
    Position afterSell = withRealisedPnl(openPosition.get(), sellAtAverageCost(order));
    if (isSellable(order.averagePrice())) {
      openPosition = Optional.of(afterSell);
      return Optional.of(new PositionWrite(afterSell));
    }
    heldQuantity = BigDecimal.ZERO;
    heldCost = BigDecimal.ZERO;
    openPosition = Optional.empty();
    return Optional.of(new PositionWrite(closed(afterSell, order.transactTime())));
  }

  /**
   * Takes the sold coins out at the average cost and returns the realised P&L of the sell, fee
   * included: sold × exit price − their share of the cost − fee. A sell of more than is held counts
   * only what is held.
   */
  private BigDecimal sellAtAverageCost(ExecutedOrder order) {
    BigDecimal exitPrice = order.averagePrice();
    BigDecimal fee =
        fees(order, QUOTE_ASSET)
            .add(fees(order, baseAsset).multiply(exitPrice))
            .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
    BigDecimal sold = order.executedQuantity().min(heldQuantity);
    BigDecimal soldCost =
        heldCost.multiply(sold).divide(heldQuantity, AMOUNT_SCALE, RoundingMode.HALF_EVEN);
    heldQuantity = heldQuantity.subtract(sold);
    heldCost = heldCost.subtract(soldCost);
    return exitPrice
        .multiply(sold)
        .subtract(soldCost)
        .subtract(fee)
        .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
  }

  private static BigDecimal fees(ExecutedOrder order, String asset) {
    return order.fills().stream()
        .filter(fill -> asset.equals(fill.feeCurrency()))
        .map(OrderFill::feeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Position withExcursions(Position position, BigDecimal price) {
    BigDecimal unrealisedPnl =
        price
            .multiply(heldQuantity)
            .subtract(heldCost)
            .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
    return new Position(
        position.runId(),
        broker,
        ticker,
        position.status(),
        position.openedAt(),
        position.closedAt(),
        position.cost(),
        position.realisedPnl(),
        position.mae().min(unrealisedPnl),
        position.mfe().max(unrealisedPnl));
  }

  private Position opened(Instant openedAt, BigDecimal cost) {
    return new Position(
        currentRunId(),
        broker,
        ticker,
        PositionStatus.OPEN,
        openedAt,
        Optional.empty(),
        cost,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static Position withCost(Position position, BigDecimal addedCost) {
    return new Position(
        position.runId(),
        position.broker(),
        position.ticker(),
        position.status(),
        position.openedAt(),
        position.closedAt(),
        position.cost().add(addedCost),
        position.realisedPnl(),
        position.mae(),
        position.mfe());
  }

  /** True while what is left is worth at least the minimum order value at this price. */
  private boolean isSellable(BigDecimal price) {
    return quantity().multiply(price).compareTo(OrderCheckerService.MIN_ORDER_VALUE) >= 0;
  }

  private static Position withRealisedPnl(Position position, BigDecimal addedPnl) {
    return new Position(
        position.runId(),
        position.broker(),
        position.ticker(),
        position.status(),
        position.openedAt(),
        position.closedAt(),
        position.cost(),
        position.realisedPnl().add(addedPnl),
        position.mae(),
        position.mfe());
  }

  private static Position closed(Position position, Instant closedAt) {
    return new Position(
        position.runId(),
        position.broker(),
        position.ticker(),
        PositionStatus.CLOSED,
        position.openedAt(),
        Optional.of(closedAt),
        position.cost(),
        position.realisedPnl(),
        position.mae(),
        position.mfe());
  }

  private long currentRunId() {
    return runId.orElseThrow(() -> new IllegalStateException("No run started for " + ticker));
  }
}
