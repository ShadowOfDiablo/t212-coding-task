package com.tihomir.t212.tradingbot.bot.tuning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.FakeAlgorithmStore;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.exception.InvalidAlgorithmSettingsException;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.PeriodSellingTestAlgorithm;
import com.tihomir.t212.tradingbot.bot.exception.NoSuccessfulTuningTrialException;
import com.tihomir.t212.tradingbot.bot.exception.TrainingAlreadyRunningException;
import com.tihomir.t212.tradingbot.bot.exception.TrainingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.run.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.run.FakeAlgorithmRunStore;
import com.tihomir.t212.tradingbot.bot.run.RunDetails;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.run.UnrecordedRun;
import com.tihomir.t212.tradingbot.bot.training.TrainingLock;
import com.tihomir.t212.tradingbot.bot.training.TrainingRunRequest;
import com.tihomir.t212.tradingbot.bot.training.TrainingService;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.history.FakeCandleStore;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import com.tihomir.t212.tradingbot.order.FakeOrderStore;
import com.tihomir.t212.tradingbot.order.OrderCheckerService;
import com.tihomir.t212.tradingbot.order.OrderService;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import com.tihomir.t212.tradingbot.position.FakePositionStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TuningServiceTest {

  private static final String BROKER = "binance";
  private static final String TICKER = FakeBroker.TRADED_TICKER;
  private static final CandleInterval TIMEFRAME = CandleInterval.FIVE_MINUTES;
  private static final Instant START = Instant.parse("2020-01-01T00:00:00Z");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final BigDecimal STARTING_BALANCE = new BigDecimal("1000");
  private static final BigDecimal BASE_ORDER_SIZE_PERCENT = new BigDecimal("50");
  private static final long TICKER_ID = 1;
  private static final int TRIAL_COUNT = 30;
  private static final int CHECK_FROM_INDEX = 20;
  private static final int CANDLE_COUNT = 30;
  private static final int TOP_INDEX = 10;
  private static final int BOTTOM_PRICE = 100;
  private static final int PRICE_STEP = 10;
  private static final int BEST_TIME_PERIOD = 10;
  private static final int ALWAYS_WORKING = Integer.MAX_VALUE;
  private static final int NEVER_WORKING = 0;
  private static final int FULL_PERCENT = 100;
  private static final TunedSettingRange TIME_PERIOD_RANGE =
      new TunedSettingRange(TradingAlgorithm.TIME_PERIOD, BigDecimal.TWO, new BigDecimal("19"));
  private static final List<TunedSettingRange> STOP_RANGES =
      List.of(
          TIME_PERIOD_RANGE,
          new TunedSettingRange(TradingAlgorithm.STOP_LOSS_PERCENT, BigDecimal.ONE, BigDecimal.TEN),
          new TunedSettingRange(
              TradingAlgorithm.TAKE_PROFIT_PERCENT, BigDecimal.ONE, BigDecimal.TEN));

  private final FakeExchangeTradingGateway liveGateway = new FakeExchangeTradingGateway();
  private final FakeCandleStore candleStore = new FakeCandleStore();
  private final FakeAlgorithmRunStore runStore = new FakeAlgorithmRunStore();
  private final BotModeService botModeService = new BotModeService(BotMode.TRAINING);
  private final TrainingLock trainingLock = new TrainingLock();
  private final AlgorithmSettingsService settingsService =
      new AlgorithmSettingsService(new FakeAlgorithmStore(1), CLOCK);
  private AlgorithmRegistryService algorithms;

  @BeforeEach
  void storeCandlesThatPeakAtTheTopIndex() {
    candleStore.storeCandles(
        TICKER_ID, IntStream.range(0, CANDLE_COUNT).mapToObj(TuningServiceTest::candle).toList());
  }

  @Test
  void recordsOnlyTheBestTryOverTheWholeRangeWithAutomaticSettings() {
    TrainingService trainingService =
        trainingService(new PeriodSellingTestAlgorithm(ALWAYS_WORKING));

    TuningResult result =
        tuningService(trainingService).runTuning(request(List.of(TIME_PERIOD_RANGE), List.of()));

    assertThat(runStore.getRuns(BROKER).runs()).hasSize(1);
    RunDetails details = runDetails(result.runId());
    assertThat(details.run().settingsSource()).isEqualTo(SettingsSource.AUTOMATIC);
    assertThat(details.run().from()).contains(openTime(0));
    assertThat(details.run().to()).contains(openTime(CANDLE_COUNT));
    assertThat(details.settings().timePeriod()).isEqualTo(BEST_TIME_PERIOD);
    assertThat(details.settings().orderSizePercent()).isEqualByComparingTo(BASE_ORDER_SIZE_PERCENT);
    assertThat(result.trialCount()).isEqualTo(TRIAL_COUNT);
    assertThat(result.bestTuneReturnPercent()).isPositive();
    assertThat(result.baseRun()).hasValueSatisfying(this::isReplayedOverTheWholeRange);
    assertThat(runStore.getRuns(BROKER).runs()).hasSize(1);
    assertThat(trainingService.isTrainingRunning()).isFalse();
  }

  @Test
  void tunesTheSameRequestTheSameWayEveryTime() {
    PeriodSellingTestAlgorithm firstAlgorithm = new PeriodSellingTestAlgorithm(ALWAYS_WORKING);
    PeriodSellingTestAlgorithm secondAlgorithm = new PeriodSellingTestAlgorithm(ALWAYS_WORKING);

    TuningResult first =
        tuningService(trainingService(firstAlgorithm)).runTuning(request(STOP_RANGES, List.of()));
    TuningResult second =
        tuningService(trainingService(secondAlgorithm)).runTuning(request(STOP_RANGES, List.of()));

    RunDetails firstRun = runDetails(first.runId());
    RunDetails secondRun = runDetails(second.runId());
    assertThat(secondAlgorithm.seenSettings()).isEqualTo(firstAlgorithm.seenSettings());
    assertThat(second.runId()).isNotEqualTo(first.runId());
    assertThat(second.bestTuneReturnPercent()).isEqualTo(first.bestTuneReturnPercent());
    assertThat(secondRun.settings()).isEqualTo(firstRun.settings());
    assertThat(secondRun.run().finalValue()).isEqualTo(firstRun.run().finalValue());
    assertThat(secondRun.points()).isEqualTo(firstRun.points());
  }

  @Test
  void reportsTheShareOfTriesDoneOnlyWhileTuning() {
    AtomicReference<TuningService> serviceHolder = new AtomicReference<>();
    List<Integer> seenPercents = new CopyOnWriteArrayList<>();
    TuningService tuningService =
        tuningService(
            trainingService(
                new PeriodSellingTestAlgorithm(
                    ALWAYS_WORKING,
                    () -> serviceHolder.get().progressPercent().ifPresent(seenPercents::add))));
    serviceHolder.set(tuningService);

    tuningService.runTuning(request(List.of(TIME_PERIOD_RANGE), List.of()));

    assertThat(seenPercents).first().isEqualTo(0);
    assertThat(seenPercents).isSorted().allMatch(percent -> percent <= FULL_PERCENT);
    assertThat(seenPercents).contains(FULL_PERCENT);
    assertThat(tuningService.progressPercent()).isEmpty();
  }

  @Test
  void scoresFailingTriesAsTheWorstAndGoesOn() {
    int maxWorkingTimePeriod = BEST_TIME_PERIOD + 2;
    PeriodSellingTestAlgorithm algorithm = new PeriodSellingTestAlgorithm(maxWorkingTimePeriod);
    CandleInterval timeframeWithoutCandles = CandleInterval.ONE_DAY;

    TuningResult result =
        tuningService(trainingService(algorithm))
            .runTuning(
                request(List.of(TIME_PERIOD_RANGE), List.of(TIMEFRAME, timeframeWithoutCandles)));

    RunDetails details = runDetails(result.runId());
    assertThat(details.settings().timePeriod()).isEqualTo(BEST_TIME_PERIOD);
    assertThat(details.settings().timeframe()).isEqualTo(TIMEFRAME);
    assertThat(algorithm.seenSettings())
        .anyMatch(settings -> settings.timePeriod() > maxWorkingTimePeriod);
    assertThat(result.trialCount()).isEqualTo(TRIAL_COUNT);
  }

  @Test
  void triesTheStopLossAndTheTakeProfitEachOnAndOff() {
    PeriodSellingTestAlgorithm algorithm = new PeriodSellingTestAlgorithm(ALWAYS_WORKING);

    tuningService(trainingService(algorithm)).runTuning(request(STOP_RANGES, List.of()));

    List<AlgorithmSettings> seen = algorithm.seenSettings();
    assertThat(seen).anyMatch(settings -> settings.stopLossPercent().isPresent());
    assertThat(seen).anyMatch(settings -> settings.stopLossPercent().isEmpty());
    assertThat(seen).anyMatch(settings -> settings.takeProfitPercent().isPresent());
    assertThat(seen).anyMatch(settings -> settings.takeProfitPercent().isEmpty());
    assertThat(seen)
        .anyMatch(settings ->
            settings.stopLossPercent().isPresent() && settings.takeProfitPercent().isEmpty());
  }

  @Test
  void recordsNothingWhenEveryTryFails() {
    TrainingService trainingService =
        trainingService(new PeriodSellingTestAlgorithm(NEVER_WORKING));
    TuningService tuningService = tuningService(trainingService);

    assertThatThrownBy(
            () -> tuningService.runTuning(request(List.of(TIME_PERIOD_RANGE), List.of())))
        .isInstanceOf(NoSuccessfulTuningTrialException.class);
    assertThat(runStore.getRuns(BROKER).runs()).isEmpty();
    assertThat(trainingService.isTrainingRunning()).isFalse();
  }

  @Test
  void refusesRangesThatCannotBeTunedBeforeAnyTry() {
    PeriodSellingTestAlgorithm algorithm = new PeriodSellingTestAlgorithm(ALWAYS_WORKING);
    TuningService tuningService = tuningService(trainingService(algorithm));

    assertRefused(tuningService, range("unknownSetting", 1, 2));
    assertRefused(tuningService, range(TradingAlgorithm.UPDATE_TIME_SECONDS, 1, 2));
    assertRefused(tuningService, range(TradingAlgorithm.TIME_PERIOD, 0, 10));
    assertRefused(tuningService, range(TradingAlgorithm.STOP_LOSS_PERCENT, 1, 100));
    assertRefused(tuningService, TIME_PERIOD_RANGE, TIME_PERIOD_RANGE);
    assertRefused(
        tuningService,
        new TunedSettingRange(TradingAlgorithm.TIME_PERIOD, new BigDecimal("2.5"), BigDecimal.TEN));
    assertRefused(tuningService);

    assertThat(algorithm.seenSettings()).isEmpty();
    assertThat(runStore.getRuns(BROKER).runs()).isEmpty();
  }

  @Test
  void refusesToTuneOutsideTrainingModeOrBesideATrainingRun() {
    TuningService tuningService =
        tuningService(trainingService(new PeriodSellingTestAlgorithm(ALWAYS_WORKING)));
    TuningRequest request = request(List.of(TIME_PERIOD_RANGE), List.of());

    trainingLock.lock();
    assertThatThrownBy(() -> tuningService.runTuning(request))
        .isInstanceOf(TrainingAlreadyRunningException.class);
    trainingLock.unlock();
    botModeService.setBotMode(BotMode.TRADING);
    assertThatThrownBy(() -> tuningService.runTuning(request))
        .isInstanceOf(TrainingModeRequiredException.class);
    assertThat(runStore.getRuns(BROKER).runs()).isEmpty();
  }

  private void isReplayedOverTheWholeRange(UnrecordedRun baseRun) {
    assertThat(baseRun.points().points()).hasSize(CANDLE_COUNT);
    assertThat(baseRun.points().points().getFirst().time()).isEqualTo(openTime(1).minusMillis(1));
  }

  private static void assertRefused(TuningService tuningService, TunedSettingRange... ranges) {
    assertThatThrownBy(() -> tuningService.runTuning(request(List.of(ranges), List.of())))
        .isInstanceOf(InvalidAlgorithmSettingsException.class);
  }

  private TuningService tuningService(TrainingService trainingService) {
    return new TuningService(trainingService, algorithms, trainingLock);
  }

  private TrainingService trainingService(TradingAlgorithm algorithm) {
    BrokerRegistryService brokers =
        new BrokerRegistryService(List.of(new FakeBroker(BROKER, liveGateway)));
    algorithms = new AlgorithmRegistryService(List.of(algorithm));
    TrainingService trainingService =
        new TrainingService(
            botModeService,
            algorithms,
            settingsService,
            new CandleHistoryService(brokers, candleStore, CLOCK),
            new OrderCheckerService(new OrderService(brokers, new FakeOrderStore(), CLOCK)),
            runStore,
            trainingLock,
            CLOCK);
    return trainingService;
  }

  private RunDetails runDetails(long runId) {
    return new AlgorithmRunService(runStore, settingsService, new FakePositionStore())
        .getRun(BROKER, runId);
  }

  private static TuningRequest request(
      List<TunedSettingRange> ranges, List<CandleInterval> timeframes) {
    return new TuningRequest(
        new TrainingRunRequest(
            BROKER,
            TICKER,
            openTime(0),
            openTime(CANDLE_COUNT),
            new AlgorithmSettingsRequest(
                AlgorithmType.SIMPLE_MOVING_AVERAGE,
                TIMEFRAME,
                true,
                Map.of(TradingAlgorithm.ORDER_SIZE_PERCENT, BASE_ORDER_SIZE_PERCENT)),
            STARTING_BALANCE),
        openTime(CHECK_FROM_INDEX),
        ranges,
        timeframes,
        TRIAL_COUNT);
  }

  private static TunedSettingRange range(String name, int minimum, int maximum) {
    return new TunedSettingRange(name, BigDecimal.valueOf(minimum), BigDecimal.valueOf(maximum));
  }

  /** Rises by 10 up to the top index, then falls by 10. */
  private static CandleUpdate candle(int index) {
    int stepsFromTop = Math.abs(TOP_INDEX - index);
    BigDecimal price = BigDecimal.valueOf(BOTTOM_PRICE + PRICE_STEP * (TOP_INDEX - stepsFromTop));
    return new CandleUpdate(
        TICKER,
        TIMEFRAME,
        openTime(index),
        openTime(index + 1).minusMillis(1),
        price,
        price,
        price,
        price,
        BigDecimal.ONE,
        true,
        openTime(index + 1).minusMillis(1));
  }

  private static Instant openTime(int index) {
    return START.plus(TIMEFRAME.duration().multipliedBy(index));
  }
}
