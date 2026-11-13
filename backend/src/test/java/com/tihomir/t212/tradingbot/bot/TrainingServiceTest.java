package com.tihomir.t212.tradingbot.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.FakeAlgorithmStore;
import com.tihomir.t212.tradingbot.algorithm.InvalidAlgorithmSettingsException;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.history.FakeCandleStore;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import com.tihomir.t212.tradingbot.order.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.FakeOrderStore;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.OrderService;
import com.tihomir.t212.tradingbot.order.TickerNotTradedException;
import com.tihomir.t212.tradingbot.position.FakePositionStore;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.PositionWrite;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TrainingServiceTest {

  private static final String BROKER = "binance";
  private static final String TICKER = FakeBroker.TRADED_TICKER;
  private static final CandleInterval TIMEFRAME = CandleInterval.FIVE_MINUTES;
  private static final Instant START = Instant.parse("2020-01-01T00:00:00Z");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final BigDecimal STARTING_BALANCE = new BigDecimal("1000");
  private static final long TICKER_ID = 1;

  private final FakeExchangeTradingGateway liveGateway = new FakeExchangeTradingGateway();
  private final FakeCandleStore candleStore = new FakeCandleStore();
  private final FakeAlgorithmRunStore runStore = new FakeAlgorithmRunStore();
  private final BotModeService botModeService = new BotModeService(BotMode.TRAINING);

  @Test
  void fillsAtTheNextCandleOpenOnTheVirtualAccountAndNeverTouchesTheLiveOne() {
    storeCandles(
        candle(0, 100, 100), candle(1, 200, 200), candle(2, 300, 300), candle(3, 400, 400));
    TrainingService service =
        service(List.of(TradingSignal.BUY, TradingSignal.HOLD, TradingSignal.SELL), () -> {});

    RunDetails details = runDetails(service.runTraining(request(4, Map.of())));

    assertThat(details.points().points())
        .extracting(PortfolioPoint::value)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(
            new BigDecimal("1000"),
            new BigDecimal("999"),
            new BigDecimal("1498.5"),
            new BigDecimal("1996.002"));
    assertThat(details.run().status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(details.run().counts()).isEqualTo(new RunCounts(1, 1, 0, 0));
    assertThat(details.run().finalValue()).hasValueSatisfying(this::isFinalValueOfTheTrade);
    assertThat(liveGateway.placedOrders()).isEmpty();
  }

  @Test
  void endsTheRunWithItsClosedPositionAndTheOneStillOpen() {
    storeCandles(
        candle(0, 100, 100),
        candle(1, 200, 200),
        candle(2, 300, 300),
        candle(3, 400, 400),
        candle(4, 400, 400));
    TrainingService service =
        service(
            List.of(TradingSignal.BUY, TradingSignal.HOLD, TradingSignal.SELL, TradingSignal.BUY),
            () -> {});

    long runId = service.runTraining(request(5, Map.of()));

    assertThat(runStore.positionWrites(runId).writes())
        .extracting(write -> write.position().status())
        .containsExactly(
            PositionStatus.OPEN, PositionStatus.CLOSED, PositionStatus.OPEN, PositionStatus.OPEN);
    PositionWrite closing = runStore.positionWrites(runId).writes().get(1);
    assertThat(closing.position().realisedPnl()).isEqualByComparingTo("996.002");
    assertThat(closing.position().mfe()).isPositive();
    assertThat(closing.newTrades().trades())
        .singleElement()
        .satisfies(trade -> assertThat(trade.runId()).isEqualTo(runId));
  }

  @Test
  void recordsTheRunWithTheSettingsOfItsAlgorithmVariant() {
    storeCandles(candle(0, 100, 100), candle(1, 100, 100));

    RunDetails details = runDetails(service(List.of(), () -> {}).runTraining(request(2, Map.of())));

    assertThat(details.settings().timeframe()).isEqualTo(TIMEFRAME);
    assertThat(runStore.activeAlgorithmId(TICKER)).isEmpty();
    assertThat(details.run().from()).contains(openTime(0));
  }

  @Test
  void sellsAtTheStopLossAndCapsTheBuyByTheRiskShare() {
    storeCandles(
        candle(0, 100, 100), candle(1, 100, 100), candle(2, 99, 97, 99), candle(3, 99, 99));
    Map<String, BigDecimal> riskLimits =
        Map.of(
            TradingAlgorithm.RISK_PER_TRADE_PERCENT,
            BigDecimal.ONE,
            TradingAlgorithm.STOP_LOSS_PERCENT,
            BigDecimal.TWO);

    RunDetails details =
        runDetails(
            service(List.of(TradingSignal.BUY), () -> {}).runTraining(request(4, riskLimits)));

    assertThat(details.points().points().get(1).cash()).isEqualByComparingTo("500");
    assertThat(details.run().counts()).isEqualTo(new RunCounts(1, 1, 1, 0));
  }

  @Test
  void countsRefusedOrdersAndIgnoresASignalOnTheLastCandle() {
    storeCandles(
        candle(0, 100, 100), candle(1, 100, 100), candle(2, 100, 100), candle(3, 100, 100));
    TrainingService service =
        service(
            List.of(TradingSignal.SELL, TradingSignal.BUY, TradingSignal.BUY, TradingSignal.BUY),
            () -> {});

    RunDetails details = runDetails(service.runTraining(request(4, Map.of())));

    assertThat(details.run().counts()).isEqualTo(new RunCounts(1, 0, 0, 2));
  }

  @Test
  void locksTheSettingsAndRefusesAnotherRunWhileOneGoesOn() {
    storeCandles(candle(0, 100, 100), candle(1, 100, 100));
    AtomicReference<TrainingService> serviceHolder = new AtomicReference<>();
    AtomicBoolean editableDuringRun = new AtomicBoolean(true);
    AtomicBoolean secondRunRefused = new AtomicBoolean();
    TrainingService service =
        service(
            List.of(),
            () -> {
              editableDuringRun.set(serviceHolder.get().areParametersEditable());
              secondRunRefused.set(isRefused(serviceHolder.get()));
            });
    serviceHolder.set(service);

    service.runTraining(request(2, Map.of()));

    assertThat(editableDuringRun).isFalse();
    assertThat(secondRunRefused).isTrue();
    assertThat(service.areParametersEditable()).isTrue();
  }

  @Test
  void refusesToRunOutsideTrainingModeOrWithBadSettingsBeforeRecordingAnything() {
    TrainingService service = service(List.of(), () -> {});
    Map<String, BigDecimal> riskWithoutStop =
        Map.of(TradingAlgorithm.RISK_PER_TRADE_PERCENT, BigDecimal.ONE);

    assertThatThrownBy(() -> service.runTraining(request(2, riskWithoutStop)))
        .isInstanceOf(InvalidAlgorithmSettingsException.class);
    assertThatThrownBy(() ->
            service.runTraining(
                new TrainingRunRequest(
                    BROKER,
                    "DOGEUSDT",
                    openTime(0),
                    openTime(2),
                    settings(Map.of()),
                    STARTING_BALANCE)))
        .isInstanceOf(TickerNotTradedException.class);
    botModeService.setBotMode(BotMode.TRADING);
    assertThatThrownBy(() -> service.runTraining(request(2, Map.of())))
        .isInstanceOf(TrainingModeRequiredException.class);
    assertThat(runStore.getRuns(BROKER).runs()).isEmpty();
  }

  @Test
  void recordsARunWithoutCandlesAsFailed() {
    TrainingService service = service(List.of(), () -> {});
    storeCandles(candle(0, 100, 100), candle(5, 100, 100));
    TrainingRunRequest gapInTheStoredCandles =
        new TrainingRunRequest(
            BROKER, TICKER, openTime(2), openTime(4), settings(Map.of()), STARTING_BALANCE);

    assertThatThrownBy(() -> service.runTraining(gapInTheStoredCandles))
        .isInstanceOf(IllegalArgumentException.class);

    RecordedRun run = runStore.getRuns(BROKER).runs().getFirst();
    assertThat(run.status()).isEqualTo(RunStatus.FAILED);
    assertThat(run.failureReason()).contains("There are no closed candles in this range");
    assertThat(service.areParametersEditable()).isTrue();
  }

  private void isFinalValueOfTheTrade(BigDecimal finalValue) {
    assertThat(finalValue).isEqualByComparingTo("1996.002");
  }

  private boolean isRefused(TrainingService service) {
    try {
      service.runTraining(request(2, Map.of()));
      return false;
    } catch (TrainingAlreadyRunningException e) {
      return true;
    }
  }

  private RunDetails runDetails(long runId) {
    AlgorithmRunService runService =
        new AlgorithmRunService(runStore, settingsService(), new FakePositionStore());
    return runService.getRun(BROKER, runId);
  }

  private TrainingService service(List<TradingSignal> signals, Runnable onEveryPrice) {
    CandleHistoryService candleHistory =
        new CandleHistoryService(
            new BrokerRegistryService(List.of(new FakeBroker(BROKER, liveGateway))),
            candleStore,
            CLOCK);
    return new TrainingService(
        botModeService,
        new AlgorithmRegistryService(List.of(new ScriptedTestAlgorithm(signals, onEveryPrice))),
        settingsService(),
        candleHistory,
        new OrderCheckerService(
            new OrderService(
                new BrokerRegistryService(List.of(new FakeBroker(BROKER, liveGateway))),
                new FakeOrderStore(),
                CLOCK)),
        runStore,
        CLOCK);
  }

  private final AlgorithmSettingsService sharedSettingsService =
      new AlgorithmSettingsService(new FakeAlgorithmStore(1), CLOCK);

  private AlgorithmSettingsService settingsService() {
    return sharedSettingsService;
  }

  private void storeCandles(CandleUpdate... candles) {
    candleStore.storeCandles(TICKER_ID, List.of(candles));
  }

  private static TrainingRunRequest request(int candleCount, Map<String, BigDecimal> values) {
    return new TrainingRunRequest(
        BROKER, TICKER, openTime(0), openTime(candleCount), settings(values), STARTING_BALANCE);
  }

  private static AlgorithmSettingsRequest settings(Map<String, BigDecimal> values) {
    return new AlgorithmSettingsRequest(
        AlgorithmType.SIMPLE_MOVING_AVERAGE, TIMEFRAME, true, new HashMap<>(values));
  }

  private static CandleUpdate candle(int index, int open, int close) {
    return candle(index, open, close, close);
  }

  private static CandleUpdate candle(int index, int open, int low, int close) {
    return new CandleUpdate(
        TICKER,
        TIMEFRAME,
        openTime(index),
        openTime(index + 1).minusMillis(1),
        BigDecimal.valueOf(open),
        BigDecimal.valueOf(Math.max(open, close)),
        BigDecimal.valueOf(low),
        BigDecimal.valueOf(close),
        BigDecimal.ONE,
        true,
        openTime(index + 1).minusMillis(1));
  }

  private static Instant openTime(int index) {
    return START.plus(TIMEFRAME.duration().multipliedBy(index));
  }
}
