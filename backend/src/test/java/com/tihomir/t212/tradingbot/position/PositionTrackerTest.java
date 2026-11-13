package com.tihomir.t212.tradingbot.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionTrackerTest {

  private static final String BROKER = "binance";
  private static final String TICKER = "BTCUSDT";
  private static final long RUN_ID = 3;
  private static final long NEXT_RUN_ID = 4;
  private static final long RESTORED_BUY_ORDER_ID = 9;
  private static final Instant FIRST_BUY = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant SECOND_BUY = Instant.parse("2026-01-01T11:00:00Z");
  private static final Instant SELL = Instant.parse("2026-01-01T12:00:00Z");
  private static final Instant SECOND_SELL = Instant.parse("2026-01-01T13:00:00Z");
  private static final Instant LAST_SELL = Instant.parse("2026-01-01T14:00:00Z");

  private final PositionTracker tracker = new PositionTracker(BROKER, TICKER);

  @BeforeEach
  void startRun() {
    tracker.startRun(RUN_ID);
  }

  @Test
  void opensAPositionWithALotWhoseEntryPriceHoldsTheFeeTakenInCoins() {
    PositionWrite write =
        tracker
            .recordExecutedOrder(
                order(OrderSide.BUY, "2", "200", "100", FIRST_BUY, fill("0.002", "BTC")))
            .orElseThrow();

    assertThat(write.position().status()).isEqualTo(PositionStatus.OPEN);
    assertThat(write.position().openedAt()).isEqualTo(FIRST_BUY);
    assertThat(write.position().cost()).isEqualByComparingTo("200");
    assertThat(write.newTrades().trades()).isEmpty();
    assertThat(write.openLots().lots())
        .singleElement()
        .satisfies(lot -> {
          assertThat(lot.runId()).isEqualTo(RUN_ID);
          assertThat(lot.quantity()).isEqualByComparingTo("1.998");
          assertThat(lot.entryPrice()).isEqualByComparingTo("100.10010010");
        });
  }

  @Test
  void sellsTheOldestLotsFirstAndSharesTheSellFeeByQuantity() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", FIRST_BUY));
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "110", "110", SECOND_BUY));

    PositionWrite write =
        tracker
            .recordExecutedOrder(
                order(OrderSide.SELL, "2", "240", "120", SELL, fill("0.24", "USDT")))
            .orElseThrow();

    assertThat(write.newTrades().trades())
        .extracting(Trade::buyOrderId, Trade::sellOrderId)
        .containsExactly(
            tuple(orderId(FIRST_BUY), orderId(SELL)), tuple(orderId(SECOND_BUY), orderId(SELL)));
    assertThat(write.newTrades().trades())
        .extracting(Trade::boughtAt, Trade::entryPrice, Trade::fee, Trade::realisedPnl)
        .containsExactly(
            tuple(
                FIRST_BUY,
                new BigDecimal("100.00000000"),
                new BigDecimal("0.12000000"),
                new BigDecimal("19.88000000")),
            tuple(
                SECOND_BUY,
                new BigDecimal("110.00000000"),
                new BigDecimal("0.12000000"),
                new BigDecimal("9.88000000")));
    assertThat(write.position().status()).isEqualTo(PositionStatus.CLOSED);
    assertThat(write.position().closedAt()).contains(SELL);
    assertThat(write.position().cost()).isEqualByComparingTo("210");
    assertThat(write.position().realisedPnl()).isEqualByComparingTo("29.76");
    assertThat(write.openLots().lots()).isEmpty();
    assertThat(tracker.quantity()).isZero();
    assertThat(tracker.entryPrice()).isEmpty();
  }

  @Test
  void splitsALotWhenASellUsesOnlyPartOfItAndKeepsTheRestOpen() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", FIRST_BUY));
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", SECOND_BUY));

    PositionWrite write =
        tracker.recordExecutedOrder(order(OrderSide.SELL, "1.5", "150", "100", SELL)).orElseThrow();

    assertThat(write.newTrades().trades())
        .extracting(Trade::boughtAt, Trade::quantity)
        .containsExactly(
            tuple(FIRST_BUY, new BigDecimal("1")), tuple(SECOND_BUY, new BigDecimal("0.5")));
    assertThat(write.position().status()).isEqualTo(PositionStatus.OPEN);
    assertThat(write.openLots().lots())
        .singleElement()
        .satisfies(lot -> {
          assertThat(lot.boughtAt()).isEqualTo(SECOND_BUY);
          assertThat(lot.quantity()).isEqualByComparingTo("0.5");
        });
  }

  @Test
  void sharesOneLotBetweenSeveralSellsUntilItIsUsedUp() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "10", "1000", "100", FIRST_BUY));

    PositionWrite firstSell =
        tracker.recordExecutedOrder(order(OrderSide.SELL, "3", "330", "110", SELL)).orElseThrow();
    PositionWrite secondSell =
        tracker
            .recordExecutedOrder(order(OrderSide.SELL, "3", "360", "120", SECOND_SELL))
            .orElseThrow();

    assertThat(List.of(firstSell, secondSell))
        .allSatisfy(write -> {
          assertThat(write.position().status()).isEqualTo(PositionStatus.OPEN);
          assertThat(write.newTrades().trades())
              .singleElement()
              .satisfies(trade -> assertThat(trade.buyOrderId()).isEqualTo(orderId(FIRST_BUY)));
        });
    assertThat(secondSell.newTrades().trades().getFirst().sellOrderId())
        .isEqualTo(orderId(SECOND_SELL));
    assertThat(secondSell.openLots().lots())
        .singleElement()
        .satisfies(lot -> assertThat(lot.quantity()).isEqualByComparingTo("4"));
    assertThat(secondSell.position().realisedPnl()).isEqualByComparingTo("90");

    PositionWrite lastSell =
        tracker
            .recordExecutedOrder(order(OrderSide.SELL, "4", "400", "100", LAST_SELL))
            .orElseThrow();

    assertThat(lastSell.position().status()).isEqualTo(PositionStatus.CLOSED);
    assertThat(lastSell.position().realisedPnl()).isEqualByComparingTo("90");
    assertThat(lastSell.openLots().lots()).isEmpty();
  }

  @Test
  void closesThePositionWhenWhatIsLeftIsWorthLessThanTheMinimumOrderValue() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", FIRST_BUY));

    PositionWrite write =
        tracker
            .recordExecutedOrder(order(OrderSide.SELL, "0.99999", "99.999", "100", SELL))
            .orElseThrow();

    assertThat(write.position().status()).isEqualTo(PositionStatus.CLOSED);
    assertThat(write.openLots().lots()).isEmpty();
    assertThat(tracker.quantity()).isZero();
  }

  @Test
  void keepsTheWorstAndBestUnrealisedPnlWhileThePositionIsOpen() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "2", "200", "100", FIRST_BUY));

    tracker.updateExcursions(new BigDecimal("90"));
    tracker.updateExcursions(new BigDecimal("115"));
    tracker.updateExcursions(new BigDecimal("105"));

    Position position = tracker.currentWrite().orElseThrow().position();
    assertThat(position.mae()).isEqualByComparingTo("-20");
    assertThat(position.mfe()).isEqualByComparingTo("30");
  }

  @Test
  void changesNothingOnASellWithoutAPositionOrABuyThatReceivedNothing() {
    assertThat(tracker.recordExecutedOrder(order(OrderSide.SELL, "1", "100", "100", SELL)))
        .isEmpty();
    assertThat(
            tracker.recordExecutedOrder(
                order(OrderSide.BUY, "0.001", "0.1", "100", FIRST_BUY, fill("0.001", "BTC"))))
        .isEmpty();
    assertThat(tracker.currentWrite()).isEmpty();
    tracker.updateExcursions(BigDecimal.ONE);
    assertThat(tracker.currentWrite()).isEmpty();
  }

  @Test
  void goesOnWithARestoredPositionAndRecordsTheSellInTheNewRun() {
    OpenLot lot =
        new OpenLot(
            RUN_ID, RESTORED_BUY_ORDER_ID, FIRST_BUY, BigDecimal.ONE, new BigDecimal("100"));
    Position stored =
        new Position(
            RUN_ID,
            BROKER,
            TICKER,
            PositionStatus.OPEN,
            FIRST_BUY,
            Optional.empty(),
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    PositionTracker restored = new PositionTracker(BROKER, TICKER);
    restored.restorePosition(new OpenPosition(stored, new OpenLotList(List.of(lot))));
    restored.startRun(NEXT_RUN_ID);

    assertThat(restored.quantity()).isEqualByComparingTo("1");
    PositionWrite write =
        restored.recordExecutedOrder(order(OrderSide.SELL, "1", "120", "120", SELL)).orElseThrow();

    assertThat(write.position().runId()).isEqualTo(RUN_ID);
    assertThat(write.newTrades().trades())
        .singleElement()
        .satisfies(trade -> {
          assertThat(trade.runId()).isEqualTo(NEXT_RUN_ID);
          assertThat(trade.realisedPnl()).isEqualByComparingTo("20");
          assertThat(trade.buyOrderId()).isEqualTo(RESTORED_BUY_ORDER_ID);
        });
  }

  @Test
  void refusesNonUsdtTickersAndOrdersBeforeARunStarted() {
    assertThatThrownBy(() -> new PositionTracker(BROKER, "ETHBTC"))
        .isInstanceOf(IllegalArgumentException.class);
    PositionTracker withoutRun = new PositionTracker(BROKER, TICKER);
    assertThatThrownBy(() ->
            withoutRun.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", FIRST_BUY)))
        .isInstanceOf(IllegalStateException.class);
  }

  private static OrderFill fill(String commission, String commissionAsset) {
    return new OrderFill(
        1, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(commission), commissionAsset);
  }

  /** Each test order gets the second of its time as its exchange order id. */
  private static long orderId(Instant time) {
    return time.getEpochSecond();
  }

  private static ExecutedOrder order(
      OrderSide side,
      String quantity,
      String quoteAmount,
      String averagePrice,
      Instant time,
      OrderFill... fills) {
    return new ExecutedOrder(
        orderId(time),
        "tb-test",
        TICKER,
        side,
        "FILLED",
        new BigDecimal(quantity),
        new BigDecimal(quoteAmount),
        new BigDecimal(averagePrice),
        List.of(fills),
        time);
  }
}
