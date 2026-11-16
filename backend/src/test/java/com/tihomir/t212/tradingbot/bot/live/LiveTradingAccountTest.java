package com.tihomir.t212.tradingbot.bot.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.OrderFill;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import com.tihomir.t212.tradingbot.position.FakePositionStore;
import com.tihomir.t212.tradingbot.position.PositionTracker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveTradingAccountTest {

  private static final String TICKER = "BTCUSDT";
  private static final long RUN_ID = 7;
  private static final BigDecimal BUDGET = new BigDecimal("1000");

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final FakeBroker broker = new FakeBroker("binance", gateway);
  private final LiveTradingAccount account =
      new LiveTradingAccount(
          TICKER, broker, new PositionTracker("binance", TICKER), new FakePositionStore(), BUDGET);

  @BeforeEach
  void startRun() {
    account.startRun(RUN_ID);
  }

  @Test
  void holdsOnlyWhatTheBotBoughtLessTheFeeTakenInCoins() {
    account.recordExecutedOrder(
        order(
            OrderSide.BUY,
            "2",
            "200",
            new OrderFill(
                1, new BigDecimal("100"), new BigDecimal("2"), new BigDecimal("0.002"), "BTC")));

    assertThat(account.cash()).isEqualByComparingTo("100");
    assertThat(account.baseQuantity()).isEqualByComparingTo("1.998");
    assertThat(account.entryPrice())
        .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo("100.10010010"));
  }

  @Test
  void averagesTheEntryPriceOverBuysAndClosesThePositionOnASellLeavingTheLotStepRest() {
    account.recordExecutedOrder(order(OrderSide.BUY, "1", "100"));
    account.recordExecutedOrder(order(OrderSide.BUY, "1.000009", "200"));

    assertThat(account.entryPrice())
        .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo("149.99932500"));

    account.recordExecutedOrder(order(OrderSide.SELL, "2", "300"));

    assertThat(account.baseQuantity()).isZero();
    assertThat(account.entryPrice()).isEmpty();
    assertThat(account.portfolioValue(BigDecimal.TEN)).isEqualByComparingTo("1000");
  }

  @Test
  void keepsItsOwnMoneyAndNeverSpendsMoreThanTheAccountHasFree() {
    gateway.changeBalances(
        Instant.EPOCH, List.of(new AssetBalance("USDT", new BigDecimal("5000"), BigDecimal.ZERO)));

    account.recordExecutedOrder(
        order(
            OrderSide.BUY,
            "0.5",
            "200",
            new OrderFill(
                1, new BigDecimal("400"), new BigDecimal("0.5"), new BigDecimal("0.2"), "USDT")));

    assertThat(account.ownCash()).isEqualByComparingTo("799.8");
    assertThat(account.cash()).isEqualByComparingTo("799.8");
    assertThat(account.portfolioValue(new BigDecimal("400"))).isEqualByComparingTo("999.8");
    gateway.changeBalances(
        Instant.EPOCH, List.of(new AssetBalance("USDT", new BigDecimal("50"), BigDecimal.ZERO)));
    assertThat(account.cash()).isEqualByComparingTo("50");
  }

  private static ExecutedOrder order(
      OrderSide side, String quantity, String quoteAmount, OrderFill... fills) {
    return new ExecutedOrder(
        1,
        "tb-test",
        TICKER,
        side,
        "FILLED",
        new BigDecimal(quantity),
        new BigDecimal(quoteAmount),
        BigDecimal.ONE,
        List.of(fills),
        Instant.EPOCH);
  }
}
