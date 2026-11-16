package com.tihomir.t212.tradingbot.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tihomir.t212.tradingbot.bot.exception.LiveBotRunningException;
import com.tihomir.t212.tradingbot.bot.live.TradingBotService;
import com.tihomir.t212.tradingbot.bot.model.LiveBotStatus;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.order.FakeOrderStore;
import com.tihomir.t212.tradingbot.order.OrderService;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.OrderAmountType;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import com.tihomir.t212.tradingbot.position.FakePositionStore;
import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountResetServiceTest {

  private static final String BROKER = "binance";
  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  private static final long RUN_ID = 3;
  private static final AssetBalance USDT =
      new AssetBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO);
  private static final AssetBalance USDC =
      new AssetBalance("USDC", new BigDecimal("50"), BigDecimal.ZERO);
  private static final AssetBalance BTC =
      new AssetBalance("BTC", new BigDecimal("0.5"), BigDecimal.ZERO);
  private static final AssetBalance NOT_FOLLOWED =
      new AssetBalance("DOGE", BigDecimal.TEN, BigDecimal.ZERO);
  private static final AssetBalance ONLY_LOCKED =
      new AssetBalance("ETH", BigDecimal.ZERO, BigDecimal.ONE);

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final FakeBroker broker = new FakeBroker(BROKER, gateway);
  private final FakePositionStore positionStore = new FakePositionStore();
  private final FakeAccountResetStore resetStore = new FakeAccountResetStore();
  private final TradingBotService tradingBotService = mock(TradingBotService.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final BrokerRegistryService brokers = new BrokerRegistryService(List.of(broker));
  private final AccountResetService service =
      new AccountResetService(
          brokers,
          new OrderService(brokers, new FakeOrderStore(), clock),
          tradingBotService,
          positionStore,
          resetStore,
          clock);

  @BeforeEach
  void fillTheAccount() {
    broker.addTicker("USDCUSDT");
    gateway.changeBalances(NOW, List.of(USDT, USDC, BTC, NOT_FOLLOWED, ONLY_LOCKED));
    when(tradingBotService.getBotStatus(BROKER)).thenReturn(Optional.empty());
  }

  @Test
  void sellsEverythingButUsdtAndClosesTheOpenPositions() {
    positionStore.savePosition(openPosition());

    EmptiedAccount emptied = service.emptyAccount(BROKER);

    assertThat(gateway.placedOrders())
        .containsExactly(
            new MarketOrderRequest(
                "USDCUSDT", OrderSide.SELL, USDC.free(), OrderAmountType.BASE_QUANTITY),
            new MarketOrderRequest(
                "BTCUSDT", OrderSide.SELL, BTC.free(), OrderAmountType.BASE_QUANTITY));
    assertThat(emptied.soldBalances()).containsExactly(USDC, BTC);
    assertThat(emptied.keptBalances()).containsExactly(NOT_FOLLOWED);
    assertThat(emptied.closedPositionCount()).isEqualTo(1);
    assertThat(positionStore.writes().writes().getLast().position())
        .satisfies(position -> assertThat(position.status()).isEqualTo(PositionStatus.CLOSED))
        .satisfies(position -> assertThat(position.closedAt()).contains(NOW));
  }

  @Test
  void keepsARefusedBalanceAndStillClosesThePositions() {
    positionStore.savePosition(openPosition());
    gateway.failOrders(new ExchangeRejectedRequestException(-1013, "Filter failure: NOTIONAL"));

    EmptiedAccount emptied = service.emptyAccount(BROKER);

    assertThat(emptied.soldBalances()).isEmpty();
    assertThat(emptied.keptBalances()).containsExactly(USDC, BTC, NOT_FOLLOWED);
    assertThat(emptied.closedPositionCount()).isEqualTo(1);
  }

  @Test
  void refusesWhileTheLiveBotRuns() {
    when(tradingBotService.getBotStatus(BROKER)).thenReturn(Optional.of(mock(LiveBotStatus.class)));

    assertThatThrownBy(() -> service.emptyAccount(BROKER))
        .isInstanceOf(LiveBotRunningException.class);
    assertThat(gateway.placedOrders()).isEmpty();
  }

  @Test
  void listsTheRecordedResets() {
    resetStore.insertAccountReset(BROKER, NOW);

    assertThat(service.getAccountResets(BROKER).resets()).containsExactly(new AccountReset(NOW));
  }

  private static PositionWrite openPosition() {
    return new PositionWrite(
        new Position(
            RUN_ID,
            BROKER,
            FakeBroker.TRADED_TICKER,
            PositionStatus.OPEN,
            NOW.minusSeconds(60),
            Optional.empty(),
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
  }
}
