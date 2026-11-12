package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderCheckerServiceTest {

  private static final BigDecimal PRICE = new BigDecimal("100");

  private final OrderCheckerService checker = new OrderCheckerService();

  @Test
  void buysWithTheOrderSizeShareOfTheCash() {
    OrderCheck check =
        checker.checkOrder(
            TradingSignal.BUY, FakeAccountView.withCash("1000"), PRICE, settings("50"));

    MarketOrderRequest order = check.order().orElseThrow();
    assertThat(order.side()).isEqualTo(OrderSide.BUY);
    assertThat(order.amountType()).isEqualTo(OrderAmountType.QUOTE_AMOUNT);
    assertThat(order.amount()).isEqualByComparingTo("500");
  }

  @Test
  void capsABuySoTheStopLossCostsAtMostTheRiskShare() {
    OrderCheck check =
        checker.checkOrder(
            TradingSignal.BUY,
            FakeAccountView.withCash("1000"),
            PRICE,
            settingsWithRisk(BigDecimal.ONE, BigDecimal.TWO));

    assertThat(check.order().orElseThrow().amount()).isEqualByComparingTo("500");
  }

  @Test
  void refusesABuyWithoutCashOrBelowTheMinimumOrderValue() {
    assertThat(
            checker
                .checkOrder(
                    TradingSignal.BUY, FakeAccountView.withCash("0"), PRICE, settings("100"))
                .refusal())
        .contains(RejectionReason.INSUFFICIENT_FUNDS);
    assertThat(
            checker
                .checkOrder(
                    TradingSignal.BUY, FakeAccountView.withCash("4"), PRICE, settings("100"))
                .refusal())
        .contains(RejectionReason.BELOW_MIN_ORDER_VALUE);
  }

  @Test
  void sellsTheWholePositionAndRefusesWithoutOne() {
    OrderCheck sell =
        checker.checkOrder(
            TradingSignal.SELL, FakeAccountView.holding("0.5", "90"), PRICE, settings("100"));

    MarketOrderRequest order = sell.order().orElseThrow();
    assertThat(order.side()).isEqualTo(OrderSide.SELL);
    assertThat(order.amountType()).isEqualTo(OrderAmountType.BASE_QUANTITY);
    assertThat(order.amount()).isEqualByComparingTo("0.5");
    assertThat(
            checker
                .checkOrder(
                    TradingSignal.SELL, FakeAccountView.withCash("10"), PRICE, settings("100"))
                .refusal())
        .contains(RejectionReason.NO_POSITION);
    assertThat(
            checker
                .checkOrder(
                    TradingSignal.SELL,
                    FakeAccountView.holding("0.01", "90"),
                    PRICE,
                    settings("100"))
                .refusal())
        .contains(RejectionReason.BELOW_MIN_ORDER_VALUE);
  }

  @Test
  void refusesToTurnHoldIntoAnOrder() {
    assertThatThrownBy(() ->
            checker.checkOrder(
                TradingSignal.HOLD, FakeAccountView.withCash("1000"), PRICE, settings("100")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reachesTheStopLossOnlyWhenOneIsSetAndSomethingIsHeld() {
    AlgorithmSettings withStop = settingsWithRisk(BigDecimal.ONE, BigDecimal.TWO);
    FakeAccountView position = FakeAccountView.holding("1", "100");

    assertThat(checker.stopPrice(position, withStop)).contains(new BigDecimal("98.00000000"));
    assertThat(checker.isStopLossReached(position, new BigDecimal("98"), withStop)).isTrue();
    assertThat(checker.isStopLossReached(position, new BigDecimal("98.01"), withStop)).isFalse();
    assertThat(checker.isStopLossReached(position, BigDecimal.ONE, settings("100"))).isFalse();
    assertThat(checker.isStopLossReached(FakeAccountView.withCash("100"), BigDecimal.ONE, withStop))
        .isFalse();
  }

  private static AlgorithmSettings settings(String orderSizePercent) {
    return new AlgorithmSettings(
        SimpleMovingAverageAlgorithm.ID,
        20,
        CandleInterval.ONE_MINUTE,
        true,
        Optional.empty(),
        new BigDecimal(orderSizePercent),
        Optional.empty(),
        Optional.empty(),
        AlgorithmSettingValues.NONE);
  }

  private static AlgorithmSettings settingsWithRisk(BigDecimal risk, BigDecimal stopLoss) {
    return new AlgorithmSettings(
        SimpleMovingAverageAlgorithm.ID,
        20,
        CandleInterval.ONE_MINUTE,
        true,
        Optional.empty(),
        new BigDecimal("100"),
        Optional.of(risk),
        Optional.of(stopLoss),
        AlgorithmSettingValues.NONE);
  }
}
