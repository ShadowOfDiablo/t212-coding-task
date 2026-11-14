package com.tihomir.t212.tradingbot.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.OrderFill;
import com.tihomir.t212.tradingbot.order.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionTrackerTest {

  private static final String BROKER = "binance";
  private static final String TICKER = "BTCUSDT";
  private static final long RUN_ID = 3;
  private static final long FILL_TRADE_ID = 1;
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
  void opensAPositionWhoseEntryPriceHoldsTheFeeTakenInCoins() {
    PositionWrite write =
        tracker
            .recordExecutedOrder(
                order(OrderSide.BUY, "2", "200", "100", FIRST_BUY, fill("0.002", "BTC")))
            .orElseThrow();

    assertThat(write.position().status()).isEqualTo(PositionStatus.OPEN);
    assertThat(write.position().runId()).isEqualTo(RUN_ID);
    assertThat(write.position().openedAt()).isEqualTo(FIRST_BUY);
    assertThat(write.position().cost()).isEqualByComparingTo("200");
    assertThat(tracker.quantity()).isEqualByComparingTo("1.998");
    assertThat(tracker.entryPrice())
        .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo("100.10010010"));
  }

  @Test
  void sellsAtTheAverageCostAndCountsTheSellFee() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", FIRST_BUY));
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "110", "110", SECOND_BUY));

    PositionWrite write =
        tracker
            .recordExecutedOrder(
                order(OrderSide.SELL, "2", "240", "120", SELL, fill("0.24", "USDT")))
            .orElseThrow();

    assertThat(write.position().status()).isEqualTo(PositionStatus.CLOSED);
    assertThat(write.position().closedAt()).contains(SELL);
    assertThat(write.position().cost()).isEqualByComparingTo("210");
    assertThat(write.position().realisedPnl()).isEqualByComparingTo("29.76");
    assertThat(tracker.quantity()).isZero();
    assertThat(tracker.entryPrice()).isEmpty();
  }

  @Test
  void keepsTheAverageEntryPriceAfterAPartialSell() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", FIRST_BUY));
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "120", "120", SECOND_BUY));

    PositionWrite write =
        tracker.recordExecutedOrder(order(OrderSide.SELL, "1", "130", "130", SELL)).orElseThrow();

    assertThat(write.position().status()).isEqualTo(PositionStatus.OPEN);
    assertThat(write.position().realisedPnl()).isEqualByComparingTo("20");
    assertThat(tracker.quantity()).isEqualByComparingTo("1");
    assertThat(tracker.entryPrice())
        .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo("110"));
  }

  @Test
  void addsThePnlOfEverySellUntilThePositionCloses() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "10", "1000", "100", FIRST_BUY));

    tracker.recordExecutedOrder(order(OrderSide.SELL, "3", "330", "110", SELL));
    PositionWrite secondSell =
        tracker
            .recordExecutedOrder(order(OrderSide.SELL, "3", "360", "120", SECOND_SELL))
            .orElseThrow();

    assertThat(secondSell.position().status()).isEqualTo(PositionStatus.OPEN);
    assertThat(secondSell.position().realisedPnl()).isEqualByComparingTo("90");
    assertThat(tracker.quantity()).isEqualByComparingTo("4");

    PositionWrite lastSell =
        tracker
            .recordExecutedOrder(order(OrderSide.SELL, "4", "400", "100", LAST_SELL))
            .orElseThrow();

    assertThat(lastSell.position().status()).isEqualTo(PositionStatus.CLOSED);
    assertThat(lastSell.position().realisedPnl()).isEqualByComparingTo("90");
  }

  @Test
  void countsOnlyTheHeldCoinsWhenASellIsBiggerThanThePosition() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", FIRST_BUY));

    PositionWrite write =
        tracker.recordExecutedOrder(order(OrderSide.SELL, "2", "240", "120", SELL)).orElseThrow();

    assertThat(write.position().status()).isEqualTo(PositionStatus.CLOSED);
    assertThat(write.position().realisedPnl()).isEqualByComparingTo("20");
  }

  @Test
  void closesThePositionWhenWhatIsLeftIsWorthLessThanTheMinimumOrderValue() {
    tracker.recordExecutedOrder(order(OrderSide.BUY, "1", "100", "100", FIRST_BUY));

    PositionWrite write =
        tracker
            .recordExecutedOrder(order(OrderSide.SELL, "0.99999", "99.999", "100", SELL))
            .orElseThrow();

    assertThat(write.position().status()).isEqualTo(PositionStatus.CLOSED);
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
        FILL_TRADE_ID, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(commission), commissionAsset);
  }

  /** Each test order gets the second of its time as its exchange order id. */
  private static ExecutedOrder order(
      OrderSide side,
      String quantity,
      String quoteAmount,
      String averagePrice,
      Instant time,
      OrderFill... fills) {
    return new ExecutedOrder(
        time.getEpochSecond(),
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
