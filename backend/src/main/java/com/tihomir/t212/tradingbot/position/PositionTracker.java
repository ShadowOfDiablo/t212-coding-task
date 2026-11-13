package com.tihomir.t212.tradingbot.position;

import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.VirtualTradingGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The bot's position on one ticker, as FIFO lots: a buy adds a lot, a sell uses up the oldest lots
 * first. Fees paid in the base or quote asset are counted; fees in any other asset are not.
 *
 * <p>Until partial sells exist, a sell closes the position. What the exchange's lot step leaves
 * behind stays in the account but is no longer part of the position.
 */
public final class PositionTracker {

  private static final Logger log = LoggerFactory.getLogger(PositionTracker.class);
  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;
  private static final int AMOUNT_SCALE = 8;

  private final String broker;
  private final String ticker;
  private final String baseAsset;
  private final Deque<OpenLot> lots = new ArrayDeque<>();
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

  /** Goes on with a position stored earlier, for example by a live bot that was stopped. */
  public synchronized void restorePosition(OpenPosition stored) {
    openPosition = Optional.of(stored.position());
    lots.clear();
    lots.addAll(stored.openLots().lots());
  }

  /** New lots, trades and positions belong to this run from now on. */
  public synchronized void startRun(long newRunId) {
    runId = Optional.of(newRunId);
  }

  public synchronized BigDecimal quantity() {
    return lots.stream().map(OpenLot::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public synchronized Optional<BigDecimal> entryPrice() {
    BigDecimal quantity = quantity();
    if (quantity.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(lotsCost().divide(quantity, AMOUNT_SCALE, RoundingMode.HALF_EVEN));
  }

  public synchronized void updateExcursions(BigDecimal price) {
    if (openPosition.isPresent()) {
      openPosition = Optional.of(withExcursions(openPosition.get(), price));
    }
  }

  /**
   * Applies an executed order to the lots.
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
    return openPosition.map(position -> write(position, TradeList.NONE));
  }

  private Optional<PositionWrite> recordBuy(ExecutedOrder order) {
    BigDecimal received = order.executedQuantity().subtract(fees(order, baseAsset));
    BigDecimal spent = order.quoteAmount().add(fees(order, QUOTE_ASSET));
    if (received.signum() <= 0) {
      log.warn("{} {}: buy {} received no coins", broker, ticker, order.exchangeOrderId());
      return Optional.empty();
    }
    Instant boughtAt = order.transactTime();
    lots.addLast(
        new OpenLot(
            currentRunId(),
            order.exchangeOrderId(),
            boughtAt,
            received,
            spent.divide(received, AMOUNT_SCALE, RoundingMode.HALF_EVEN)));
    Position position =
        openPosition.map(open -> withCost(open, spent)).orElse(opened(boughtAt, spent));
    openPosition = Optional.of(position);
    return Optional.of(write(position, TradeList.NONE));
  }

  private Optional<PositionWrite> recordSell(ExecutedOrder order) {
    if (openPosition.isEmpty()) {
      log.warn("{} {}: sell {} while no position is held", broker, ticker, order.exchangeOrderId());
      return Optional.empty();
    }
    TradeList trades = matchOldestLots(order);
    lots.clear();
    BigDecimal realisedPnl =
        trades.trades().stream().map(Trade::realisedPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
    Position closed = closed(openPosition.get(), order.transactTime(), realisedPnl);
    openPosition = Optional.empty();
    return Optional.of(write(closed, trades));
  }

  private TradeList matchOldestLots(ExecutedOrder order) {
    BigDecimal soldQuantity = order.executedQuantity();
    BigDecimal exitPrice = order.averagePrice();
    BigDecimal fee =
        fees(order, QUOTE_ASSET)
            .add(fees(order, baseAsset).multiply(exitPrice))
            .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
    List<Trade> trades = new ArrayList<>();
    BigDecimal unmatched = soldQuantity;
    while (unmatched.signum() > 0 && !lots.isEmpty()) {
      OpenLot oldest = lots.removeFirst();
      BigDecimal matched = oldest.quantity().min(unmatched);
      trades.add(trade(oldest, matched, order, feeShare(fee, matched, soldQuantity)));
      unmatched = unmatched.subtract(matched);
      if (matched.compareTo(oldest.quantity()) < 0) {
        lots.addFirst(withQuantity(oldest, oldest.quantity().subtract(matched)));
      }
    }
    return new TradeList(trades);
  }

  private Trade trade(OpenLot lot, BigDecimal quantity, ExecutedOrder sell, BigDecimal fee) {
    BigDecimal exitPrice = sell.averagePrice();
    BigDecimal realisedPnl =
        exitPrice
            .subtract(lot.entryPrice())
            .multiply(quantity)
            .subtract(fee)
            .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
    return new Trade(
        currentRunId(),
        lot.buyOrderId(),
        sell.exchangeOrderId(),
        lot.boughtAt(),
        sell.transactTime(),
        quantity,
        lot.entryPrice(),
        exitPrice,
        fee,
        realisedPnl);
  }

  private static BigDecimal feeShare(BigDecimal fee, BigDecimal matched, BigDecimal soldQuantity) {
    return fee.multiply(matched).divide(soldQuantity, AMOUNT_SCALE, RoundingMode.HALF_EVEN);
  }

  private static OpenLot withQuantity(OpenLot lot, BigDecimal quantity) {
    return new OpenLot(lot.runId(), lot.buyOrderId(), lot.boughtAt(), quantity, lot.entryPrice());
  }

  private static BigDecimal fees(ExecutedOrder order, String asset) {
    return order.fills().stream()
        .filter(fill -> asset.equals(fill.commissionAsset()))
        .map(OrderFill::commission)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal lotsCost() {
    return lots.stream()
        .map(lot -> lot.quantity().multiply(lot.entryPrice()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Position withExcursions(Position position, BigDecimal price) {
    BigDecimal unrealisedPnl =
        price
            .multiply(quantity())
            .subtract(lotsCost())
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

  private static Position closed(Position position, Instant closedAt, BigDecimal realisedPnl) {
    return new Position(
        position.runId(),
        position.broker(),
        position.ticker(),
        PositionStatus.CLOSED,
        position.openedAt(),
        Optional.of(closedAt),
        position.cost(),
        position.realisedPnl().add(realisedPnl),
        position.mae(),
        position.mfe());
  }

  private long currentRunId() {
    return runId.orElseThrow(() -> new IllegalStateException("No run started for " + ticker));
  }

  private PositionWrite write(Position position, TradeList newTrades) {
    return new PositionWrite(position, new OpenLotList(List.copyOf(lots)), newTrades);
  }
}
