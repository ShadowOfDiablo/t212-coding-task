package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.OrderAmountType;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderCheckerServiceTest {

  private static final BigDecimal PRICE = new BigDecimal("100");

  private static final String BROKER = "binance";
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final OrderCheckerService checker =
      new OrderCheckerService(
          new OrderService(
              new BrokerRegistryService(List.of(new FakeBroker(BROKER, gateway))),
              new FakeOrderStore(),
              Clock.fixed(NOW, ZoneOffset.UTC)));

  @Test
  void placesALiveOrderOnlyWhenTheChecksApproveIt() {
    Optional<ExecutedOrder> placed =
        checker.placeCheckedOrder(
            BROKER, TradingSignal.BUY, FakeAccountView.withCash("1000"), PRICE, settings("50"));
    Optional<ExecutedOrder> refused =
        checker.placeCheckedOrder(
            BROKER, TradingSignal.SELL, FakeAccountView.withCash("1000"), PRICE, settings("50"));

    assertThat(placed).isPresent();
    assertThat(refused).isEmpty();
    assertThat(gateway.placedOrders())
        .singleElement()
        .satisfies(order -> assertThat(order.side()).isEqualTo(OrderSide.BUY));
  }

  @Test
  void placesNothingWhenTheExchangeRejectsTheOrder() {
    gateway.failOrders(new ExchangeRejectedRequestException(-2010, "insufficient balance"));

    assertThat(
            checker.placeCheckedOrder(
                BROKER, TradingSignal.BUY, FakeAccountView.withCash("1000"), PRICE, settings("50")))
        .isEmpty();
  }

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
    AlgorithmSettings withStop = settingsWithSellLimits(BigDecimal.TWO, BigDecimal.TEN);
    FakeAccountView position = FakeAccountView.holding("1", "100");

    assertThat(checker.stopPrice(position, withStop)).contains(new BigDecimal("98.00000000"));
    assertThat(checker.isStopLossReached(position, new BigDecimal("98"), withStop)).isTrue();
    assertThat(checker.isStopLossReached(position, new BigDecimal("98.01"), withStop)).isFalse();
    assertThat(checker.isStopLossReached(position, BigDecimal.ONE, settings("100"))).isFalse();
    assertThat(checker.isStopLossReached(FakeAccountView.withCash("100"), BigDecimal.ONE, withStop))
        .isFalse();
  }

  @Test
  void reachesTheTakeProfitOnlyWhenOneIsSetAndSomethingIsHeld() {
    AlgorithmSettings withTakeProfit = settingsWithSellLimits(BigDecimal.TWO, BigDecimal.TEN);
    FakeAccountView position = FakeAccountView.holding("1", "100");

    assertThat(checker.takeProfitPrice(position, withTakeProfit))
        .contains(new BigDecimal("110.00000000"));
    assertThat(checker.isTakeProfitReached(position, new BigDecimal("110"), withTakeProfit))
        .isTrue();
    assertThat(checker.isTakeProfitReached(position, new BigDecimal("109.99"), withTakeProfit))
        .isFalse();
    assertThat(checker.isTakeProfitReached(position, new BigDecimal("1000"), settings("100")))
        .isFalse();
    assertThat(
            checker.isTakeProfitReached(
                FakeAccountView.withCash("100"), new BigDecimal("1000"), withTakeProfit))
        .isFalse();
  }

  private static AlgorithmSettings settings(String orderSizePercent) {
    return new AlgorithmSettings(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        20,
        CandleInterval.ONE_MINUTE,
        true,
        Optional.empty(),
        new BigDecimal(orderSizePercent),
        Optional.empty(),
        Optional.empty(),
        AlgorithmSettingValues.NONE);
  }

  private static AlgorithmSettings settingsWithSellLimits(
      BigDecimal stopLoss, BigDecimal takeProfit) {
    return new AlgorithmSettings(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        20,
        CandleInterval.ONE_MINUTE,
        true,
        Optional.empty(),
        new BigDecimal("100"),
        Optional.of(stopLoss),
        Optional.of(takeProfit),
        AlgorithmSettingValues.NONE);
  }
}
