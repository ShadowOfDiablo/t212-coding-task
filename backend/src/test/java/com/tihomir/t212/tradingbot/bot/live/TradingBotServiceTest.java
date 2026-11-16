package com.tihomir.t212.tradingbot.bot.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.FakeAlgorithmStore;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.algorithm.exception.InvalidAlgorithmSettingsException;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.ScriptedTestAlgorithm;
import com.tihomir.t212.tradingbot.bot.exception.LiveBotNotRunningException;
import com.tihomir.t212.tradingbot.bot.exception.LiveBotRunningException;
import com.tihomir.t212.tradingbot.bot.exception.TradingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.live.queue.EvaluationRetryPolicy;
import com.tihomir.t212.tradingbot.bot.live.queue.PriceMessage;
import com.tihomir.t212.tradingbot.bot.model.LiveBotStatus;
import com.tihomir.t212.tradingbot.bot.model.LiveRunRequest;
import com.tihomir.t212.tradingbot.bot.run.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.run.FakeAlgorithmRunStore;
import com.tihomir.t212.tradingbot.bot.run.RecordedRun;
import com.tihomir.t212.tradingbot.bot.run.RunCounts;
import com.tihomir.t212.tradingbot.bot.run.RunDetails;
import com.tihomir.t212.tradingbot.bot.run.RunStatus;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.history.FakeCandleStore;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceWriter;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuoteSource;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import com.tihomir.t212.tradingbot.order.FakeOrderStore;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.OrderService;
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.exception.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.exception.TickerNotTradedException;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.OrderAmountType;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import com.tihomir.t212.tradingbot.position.FakePositionStore;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TradingBotServiceTest {

  private static final String BROKER = "binance";
  private static final String TICKER = FakeBroker.TRADED_TICKER;
  private static final CandleInterval TIMEFRAME = CandleInterval.ONE_MINUTE;
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant NOW = Instant.parse("2026-01-01T00:02:30Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final int TIME_PERIOD = 2;
  private static final long TICKER_ID = 1;
  private static final long ONE_HOUR_IN_SECONDS = 3600;
  private static final List<TradingSignal> WARM_UP =
      List.of(TradingSignal.HOLD, TradingSignal.HOLD);
  private static final BigDecimal TOO_HIGH_STOP_LOSS_PERCENT = BigDecimal.valueOf(100);
  private static final BigDecimal BUY_AMOUNT = new BigDecimal("100.00000000");
  private static final Map<String, BigDecimal> STOP_LOSS_AND_TAKE_PROFIT =
      Map.of(
          TradingAlgorithm.STOP_LOSS_PERCENT,
          BigDecimal.TWO,
          TradingAlgorithm.TAKE_PROFIT_PERCENT,
          BigDecimal.TEN);

  private final FakeExchangeTradingGateway gateway = new FakeExchangeTradingGateway();
  private final FakeBroker broker = new FakeBroker(BROKER, gateway);
  private final FakeCandleStore candleStore = new FakeCandleStore();
  private final FakeAlgorithmRunStore runStore = new FakeAlgorithmRunStore();
  private final FakePositionStore positionStore = new FakePositionStore();
  private final BotModeService botModeService = new BotModeService(BotMode.TRADING);
  private final AtomicInteger evaluatedPrices = new AtomicInteger();
  private final List<TradingBotService> services = new ArrayList<>();
  private final AlgorithmSettingsService settingsService =
      new AlgorithmSettingsService(new FakeAlgorithmStore(1), Clock.fixed(NOW, ZoneOffset.UTC));

  @AfterEach
  void stopTheWorkers() {
    services.forEach(TradingBotService::stopAllBots);
  }

  @Test
  void buysAndSellsOnLiveTicksAndRecordsTheStop() {
    TradingBotService service = service(TradingSignal.BUY, TradingSignal.SELL);
    long runId = service.startBot(request(true, Map.of()));
    LiveTradingBot bot = service.findRunningBot(BROKER).orElseThrow();
    updateLastPrice(BigDecimal.ONE);

    bot.onCandleUpdate(tick(TICKER, BigDecimal.ONE));
    bot.onCandleUpdate(tick(TICKER, BigDecimal.ONE));
    service.stopBot(BROKER);

    RunDetails details = runDetails(runId);
    assertThat(gateway.placedOrders())
        .containsExactly(
            new MarketOrderRequest(
                TICKER,
                OrderSide.BUY,
                new BigDecimal("100.00000000"),
                OrderAmountType.QUOTE_AMOUNT),
            new MarketOrderRequest(
                TICKER,
                OrderSide.SELL,
                new BigDecimal("100.00000000"),
                OrderAmountType.BASE_QUANTITY));
    assertThat(details.run().mode()).isEqualTo(BotMode.TRADING);
    assertThat(details.run().status()).isEqualTo(RunStatus.STOPPED);
    assertThat(details.run().counts()).isEqualTo(new RunCounts(1, 1, 0, 0, 0));
    assertThat(details.run().startingValue()).isEqualByComparingTo("100");
    assertThat(details.run().finalValue()).hasValueSatisfying(this::isTheStartingCash);
    assertThat(details.points().points())
        .singleElement()
        .satisfies(point -> assertThat(point.time()).isEqualTo(NOW));
    assertThat(runStore.activeAlgorithmId(TICKER)).contains(details.run().algorithmId());
    assertThat(service.getBotStatus(BROKER)).isEmpty();
  }

  @Test
  void sellsAtTheStopLoss() {
    TradingBotService service = service(TradingSignal.BUY);
    long runId = service.startBot(request(true, STOP_LOSS_AND_TAKE_PROFIT));
    LiveTradingBot bot = service.findRunningBot(BROKER).orElseThrow();

    bot.evaluate(BigDecimal.ONE, NOW);
    bot.evaluate(new BigDecimal("0.5"), NOW);
    service.stopBot(BROKER);

    assertThat(gateway.placedOrders())
        .extracting(MarketOrderRequest::side, MarketOrderRequest::amount)
        .containsExactly(tuple(OrderSide.BUY, BUY_AMOUNT), tuple(OrderSide.SELL, BUY_AMOUNT));
    assertThat(runDetails(runId).run().counts()).isEqualTo(new RunCounts(1, 1, 1, 0, 0));
  }

  @Test
  void sellsAtTheTakeProfit() {
    TradingBotService service = service(TradingSignal.BUY);
    long runId = service.startBot(request(true, STOP_LOSS_AND_TAKE_PROFIT));
    LiveTradingBot bot = service.findRunningBot(BROKER).orElseThrow();

    bot.evaluate(BigDecimal.ONE, NOW);
    bot.evaluate(new BigDecimal("1.1"), NOW);
    service.stopBot(BROKER);

    assertThat(gateway.placedOrders())
        .extracting(MarketOrderRequest::side)
        .containsExactly(OrderSide.BUY, OrderSide.SELL);
    assertThat(runDetails(runId).run().counts()).isEqualTo(new RunCounts(1, 1, 0, 1, 0));
  }

  @Test
  void placesNoOrderWhilePausedNotEvenAtTheStopLossAndSellsAfterResuming() {
    TradingBotService service = service(TradingSignal.BUY);
    long runId = service.startBot(request(true, STOP_LOSS_AND_TAKE_PROFIT));
    LiveTradingBot bot = service.findRunningBot(BROKER).orElseThrow();
    bot.evaluate(BigDecimal.ONE, NOW);

    service.pauseBot(BROKER);
    LiveBotStatus paused = service.pauseBot(BROKER);
    bot.evaluate(new BigDecimal("0.5"), NOW);
    int ordersWhilePaused = gateway.placedOrders().size();
    LiveBotStatus resumed = service.resumeBot(BROKER);
    bot.evaluate(new BigDecimal("0.5"), NOW);
    service.stopBot(BROKER);

    assertThat(paused.paused()).isTrue();
    assertThat(ordersWhilePaused).isEqualTo(1);
    assertThat(resumed.paused()).isFalse();
    assertThat(gateway.placedOrders())
        .extracting(MarketOrderRequest::side)
        .containsExactly(OrderSide.BUY, OrderSide.SELL);
    assertThat(runDetails(runId).run().counts()).isEqualTo(new RunCounts(1, 1, 1, 0, 0));
  }

  @Test
  void staysPausedAfterAParameterChange() {
    TradingBotService service = service();
    service.startBot(request(true, Map.of()));
    service.pauseBot(BROKER);

    LiveBotStatus changed =
        service.updateCurrentParameters(BROKER, settings(true, STOP_LOSS_AND_TAKE_PROFIT));
    service.stopBot(BROKER);

    assertThat(changed.paused()).isTrue();
  }

  @Test
  void discardsAPriceOlderThanTheLastOneEvaluated() {
    TradingBotService service = service(TradingSignal.BUY, TradingSignal.SELL);
    service.startBot(request(true, Map.of()));
    LiveTradingBot bot = service.findRunningBot(BROKER).orElseThrow();

    bot.evaluatePriceMessage(PriceMessage.firstAttempt(TICKER, BigDecimal.ONE, NOW));
    bot.evaluatePriceMessage(
        PriceMessage.firstAttempt(TICKER, BigDecimal.ONE, NOW.minusSeconds(1)));
    service.stopBot(BROKER);

    assertThat(gateway.placedOrders())
        .extracting(MarketOrderRequest::side)
        .containsExactly(OrderSide.BUY);
    assertThat(evaluatedPrices).hasValue(WARM_UP.size() + 1);
  }

  @Test
  void countsAnOrderTheChecksRefuse() {
    TradingBotService service = service(TradingSignal.SELL);
    long runId = service.startBot(request(true, Map.of()));

    service.findRunningBot(BROKER).orElseThrow().onCandleUpdate(tick(TICKER, BigDecimal.ONE));
    service.stopBot(BROKER);

    assertThat(runDetails(runId).run().counts()).isEqualTo(new RunCounts(0, 0, 0, 0, 1));
  }

  @Test
  void retriesAPriceWhoseBalanceReadFailedAndThenBuys() {
    TradingBotService service = service(TradingSignal.BUY, TradingSignal.BUY);
    long runId = service.startBot(request(true, Map.of()));
    gateway.failBalanceRequests(new ExchangeUnavailableException("timed out"), 1);

    service.findRunningBot(BROKER).orElseThrow().onCandleUpdate(tick(TICKER, BigDecimal.ONE));
    service.stopBot(BROKER);

    assertThat(gateway.placedOrders())
        .extracting(MarketOrderRequest::side)
        .containsExactly(OrderSide.BUY);
    assertThat(runDetails(runId).run().counts()).isEqualTo(new RunCounts(1, 0, 0, 0, 0));
  }

  @Test
  void retriesAnOrderTheExchangeRejectsThenCountsItOnce() {
    TradingBotService service = service(TradingSignal.BUY, TradingSignal.BUY, TradingSignal.BUY);
    long runId = service.startBot(request(true, Map.of()));
    gateway.failOrders(new ExchangeRejectedRequestException(-2010, "Not enough USDT"));

    service.findRunningBot(BROKER).orElseThrow().onCandleUpdate(tick(TICKER, BigDecimal.ONE));
    service.stopBot(BROKER);

    assertThat(runDetails(runId).run().counts()).isEqualTo(new RunCounts(0, 0, 0, 0, 1));
    assertThat(evaluatedPrices).hasValue(WARM_UP.size() + EvaluationRetryPolicy.MAX_ATTEMPTS);
  }

  @Test
  void refusesToStartOutsideTradingModeOrWithBadSettingsBeforeRecordingAnything() {
    TradingBotService service = service();
    Map<String, BigDecimal> stopLossTooHigh =
        Map.of(TradingAlgorithm.STOP_LOSS_PERCENT, TOO_HIGH_STOP_LOSS_PERCENT);

    assertThatThrownBy(() -> service.startBot(request(true, stopLossTooHigh)))
        .isInstanceOf(InvalidAlgorithmSettingsException.class);
    assertThatThrownBy(() ->
            service.startBot(new LiveRunRequest(BROKER, "DOGEUSDT", settings(true, Map.of()))))
        .isInstanceOf(TickerNotTradedException.class);
    botModeService.setBotMode(BotMode.TRAINING);
    assertThatThrownBy(() -> service.startBot(request(true, Map.of())))
        .isInstanceOf(TradingModeRequiredException.class);
    assertThat(runStore.getRuns(BROKER).runs()).isEmpty();
    assertThat(service.getBotStatus(BROKER)).isEmpty();
  }

  @Test
  void refusesASecondBotAndAModeSwitchWhileItRuns() {
    TradingBotService service = service();
    service.startBot(request(true, Map.of()));

    assertThatThrownBy(() -> service.startBot(request(true, Map.of())))
        .isInstanceOf(LiveBotRunningException.class);
    assertThatThrownBy(() -> service.switchBotMode(BotMode.TRAINING))
        .isInstanceOf(LiveBotRunningException.class);
    assertThat(botModeService.botMode()).isEqualTo(BotMode.TRADING);

    service.stopBot(BROKER);
    service.switchBotMode(BotMode.TRAINING);
    assertThat(botModeService.botMode()).isEqualTo(BotMode.TRAINING);
  }

  @Test
  void changedParametersEndTheRunAndStartANewOneAndAnInvalidChangeKeepsTheRun() {
    TradingBotService service = service();
    long firstRunId = service.startBot(request(true, Map.of()));
    long firstAlgorithmId = service.getBotStatus(BROKER).orElseThrow().algorithmId();

    LiveBotStatus changed =
        service.updateCurrentParameters(BROKER, settings(true, STOP_LOSS_AND_TAKE_PROFIT));
    assertThatThrownBy(() ->
            service.updateCurrentParameters(
                BROKER,
                settings(
                    true, Map.of(TradingAlgorithm.STOP_LOSS_PERCENT, TOO_HIGH_STOP_LOSS_PERCENT))))
        .isInstanceOf(InvalidAlgorithmSettingsException.class);
    LiveBotStatus afterInvalidChange = service.getBotStatus(BROKER).orElseThrow();
    service.stopBot(BROKER);

    assertThat(changed.runId()).isNotEqualTo(firstRunId);
    assertThat(changed.algorithmId()).isNotEqualTo(firstAlgorithmId);
    assertThat(changed.currentParameters().stopLossPercent()).isPresent();
    assertThat(afterInvalidChange.runId()).isEqualTo(changed.runId());
    assertThat(afterInvalidChange.algorithmId()).isEqualTo(changed.algorithmId());
    RecordedRun firstRun = runDetails(firstRunId).run();
    assertThat(firstRun.status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(firstRun.algorithmId()).isEqualTo(firstAlgorithmId);
    RecordedRun secondRun = runDetails(changed.runId()).run();
    assertThat(secondRun.status()).isEqualTo(RunStatus.STOPPED);
    assertThat(secondRun.algorithmId()).isEqualTo(changed.algorithmId());
    assertThat(secondRun.startedAt()).isEqualTo(firstRun.finishedAt().orElseThrow());
    assertThat(runStore.activeAlgorithmId(TICKER)).contains(changed.algorithmId());
  }

  @Test
  void stoppingOrChangingWithoutARunningBotIsRefused() {
    TradingBotService service = service();

    assertThatThrownBy(() -> service.stopBot(BROKER))
        .isInstanceOf(LiveBotNotRunningException.class);
    assertThatThrownBy(() -> service.updateCurrentParameters(BROKER, settings(true, Map.of())))
        .isInstanceOf(LiveBotNotRunningException.class);
    assertThatThrownBy(() -> service.pauseBot(BROKER))
        .isInstanceOf(LiveBotNotRunningException.class);
    assertThatThrownBy(() -> service.resumeBot(BROKER))
        .isInstanceOf(LiveBotNotRunningException.class);
  }

  @Test
  void ignoresTicksOfOtherTickersAndAllTicksWhenRunningOnATimer() {
    TradingBotService service = service(TradingSignal.BUY);
    service.startBot(request(true, Map.of()));
    service.findRunningBot(BROKER).orElseThrow().onCandleUpdate(tick("ETHUSDT", BigDecimal.ONE));
    service.stopBot(BROKER);
    service.startBot(request(false, Map.of()));
    service.findRunningBot(BROKER).orElseThrow().onCandleUpdate(tick(TICKER, BigDecimal.ONE));
    service.stopBot(BROKER);

    assertThat(gateway.placedOrders()).isEmpty();
    assertThat(evaluatedPrices).hasValue(WARM_UP.size() * 2);
  }

  @Test
  void storesTheLivePositionAfterEachOrderInTheRun() {
    TradingBotService service = service(TradingSignal.BUY, TradingSignal.SELL);
    long runId = service.startBot(request(true, Map.of()));
    LiveTradingBot bot = service.findRunningBot(BROKER).orElseThrow();

    bot.evaluate(BigDecimal.ONE, NOW);
    bot.evaluate(BigDecimal.ONE, NOW);
    service.stopBot(BROKER);

    assertThat(positionStore.writes().writes())
        .extracting(write -> write.position().status())
        .containsExactly(PositionStatus.OPEN, PositionStatus.CLOSED);
    assertThat(positionStore.writes().writes())
        .extracting(write -> write.position().runId())
        .containsOnly(runId);
  }

  @Test
  void keepsTradingWhenThePositionCannotBeStored() {
    positionStore.failWrites();
    TradingBotService service = service(TradingSignal.BUY);
    service.startBot(request(true, Map.of()));
    LiveTradingBot bot = service.findRunningBot(BROKER).orElseThrow();

    bot.evaluate(BigDecimal.ONE, NOW);

    LiveBotStatus status = service.getBotStatus(BROKER).orElseThrow();
    assertThat(status.counts().buyCount()).isEqualTo(1);
    service.stopBot(BROKER);
  }

  private void isTheStartingCash(BigDecimal finalValue) {
    assertThat(finalValue).isEqualByComparingTo("100");
  }

  private RunDetails runDetails(long runId) {
    return new AlgorithmRunService(runStore, settingsService, positionStore).getRun(BROKER, runId);
  }

  private void updateLastPrice(BigDecimal price) {
    ((TickerPriceWriter) broker.tickerPrices())
        .updateLastPrice(TICKER, price, NOW, TickerQuoteSource.WEBSOCKET);
  }

  private TradingBotService service(TradingSignal... signals) {
    storeWarmUpCandles();
    BrokerRegistryService brokers = new BrokerRegistryService(List.of(broker));
    List<TradingSignal> script =
        Stream.concat(WARM_UP.stream(), List.of(signals).stream()).toList();
    TradingBotService service =
        new TradingBotService(
            botModeService,
            new AlgorithmRegistryService(
                List.of(new ScriptedTestAlgorithm(script, evaluatedPrices::incrementAndGet))),
            settingsService,
            new CandleHistoryService(brokers, candleStore, CLOCK),
            new OrderCheckerService(new OrderService(brokers, new FakeOrderStore(), CLOCK)),
            runStore,
            positionStore,
            CLOCK);
    services.add(service);
    return service;
  }

  private void storeWarmUpCandles() {
    candleStore.storeCandles(TICKER_ID, List.of(candle(0), candle(1)));
  }

  private static LiveRunRequest request(boolean liveOnTick, Map<String, BigDecimal> values) {
    return new LiveRunRequest(BROKER, TICKER, settings(liveOnTick, values));
  }

  private static AlgorithmSettingsRequest settings(
      boolean liveOnTick, Map<String, BigDecimal> values) {
    Map<String, BigDecimal> allValues = new HashMap<>(values);
    allValues.put(TradingAlgorithm.TIME_PERIOD, BigDecimal.valueOf(TIME_PERIOD));
    if (!liveOnTick) {
      allValues.put(TradingAlgorithm.UPDATE_TIME_SECONDS, BigDecimal.valueOf(ONE_HOUR_IN_SECONDS));
    }
    return new AlgorithmSettingsRequest(
        AlgorithmType.SIMPLE_MOVING_AVERAGE, TIMEFRAME, liveOnTick, allValues);
  }

  private static CandleUpdate tick(String ticker, BigDecimal price) {
    return new CandleUpdate(
        ticker,
        TIMEFRAME,
        START.plusSeconds(120),
        START.plusSeconds(180).minusMillis(1),
        price,
        price,
        price,
        price,
        BigDecimal.ONE,
        false,
        NOW);
  }

  private static CandleUpdate candle(int index) {
    Instant openTime = START.plus(TIMEFRAME.duration().multipliedBy(index));
    return new CandleUpdate(
        TICKER,
        TIMEFRAME,
        openTime,
        openTime.plus(TIMEFRAME.duration()).minusMillis(1),
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        true,
        openTime.plus(TIMEFRAME.duration()));
  }
}
