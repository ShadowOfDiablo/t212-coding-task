package com.tihomir.t212.tradingbot.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingSignal;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.marketdata.history.CandleHistoryService;
import com.tihomir.t212.tradingbot.marketdata.history.FakeCandleStore;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import com.tihomir.t212.tradingbot.order.FakeExchangeTradingGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LiveSignalSourceTest {

  private static final String BROKER = "binance";
  private static final String TICKER = FakeBroker.TRADED_TICKER;
  private static final CandleInterval TIMEFRAME = CandleInterval.ONE_MINUTE;
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final int STORED_CANDLES = 5;
  private static final int TIME_PERIOD = 2;
  private static final long TICKER_ID = 1;

  private final FakeCandleStore candleStore = new FakeCandleStore();
  private final RecordingTestAlgorithm algorithm = new RecordingTestAlgorithm();
  private final LiveSignalSource signalSource = signalSource();

  @Test
  void warmsUpWithTheClosedCandlesAndAddsTheLivePriceAsTheFormingCandle() {
    signalSource.warmUp(at(2, 30));

    TradingSignal signal = signalSource.signalAt(BigDecimal.TEN, at(2, 40));

    assertThat(signal).isEqualTo(TradingSignal.HOLD);
    assertThat(algorithm.seenCandles())
        .extracting(CandleUpdate::openTime, CandleUpdate::closed)
        .containsExactly(tuple(at(0, 0), true), tuple(at(1, 0), true), tuple(at(2, 0), false));
    assertThat(algorithm.seenCandles().getLast().close()).isEqualByComparingTo("10");
  }

  @Test
  void readsTheCandlesThatClosedSinceTheLastPriceAndPassesOnTheirSignal() {
    signalSource.warmUp(at(2, 30));

    TradingSignal signal = signalSource.signalAt(BigDecimal.TEN, at(4, 10));

    assertThat(signal).isEqualTo(TradingSignal.BUY);
    assertThat(algorithm.seenCandles())
        .extracting(CandleUpdate::openTime)
        .containsExactly(at(0, 0), at(1, 0), at(2, 0), at(3, 0), at(4, 0));
  }

  @Test
  void retriesTheClosedCandlesAtTheNextPriceWhenTheyCantBeRead() {
    signalSource.warmUp(at(2, 30));
    candleStore.takeDatabaseDown();

    TradingSignal signalWhileDown = signalSource.signalAt(BigDecimal.TEN, at(3, 10));
    candleStore.bringDatabaseBackUp();
    TradingSignal signalAfter = signalSource.signalAt(BigDecimal.TEN, at(3, 20));

    assertThat(signalWhileDown).isEqualTo(TradingSignal.HOLD);
    assertThat(signalAfter).isEqualTo(TradingSignal.BUY);
    assertThat(algorithm.seenCandles())
        .filteredOn(CandleUpdate::closed)
        .extracting(CandleUpdate::openTime)
        .containsExactly(at(0, 0), at(1, 0), at(2, 0));
  }

  private LiveSignalSource signalSource() {
    candleStore.storeCandles(
        TICKER_ID, IntStream.range(0, STORED_CANDLES).mapToObj(this::candle).toList());
    CandleHistoryService candleHistory =
        new CandleHistoryService(
            new BrokerRegistryService(
                List.of(new FakeBroker(BROKER, new FakeExchangeTradingGateway()))),
            candleStore,
            Clock.fixed(at(STORED_CANDLES, 30), ZoneOffset.UTC));
    AlgorithmSettings settings =
        new AlgorithmSettings(
            AlgorithmType.SIMPLE_MOVING_AVERAGE,
            TIME_PERIOD,
            TIMEFRAME,
            true,
            Optional.empty(),
            BigDecimal.TEN,
            Optional.empty(),
            Optional.empty(),
            AlgorithmSettingValues.NONE);
    return new LiveSignalSource(candleHistory, BROKER, TICKER, algorithm, settings);
  }

  private CandleUpdate candle(int index) {
    BigDecimal price = BigDecimal.valueOf(index + 1);
    return new CandleUpdate(
        TICKER,
        TIMEFRAME,
        at(index, 0),
        at(index + 1, 0).minusMillis(1),
        price,
        price,
        price,
        price,
        BigDecimal.ONE,
        true,
        at(index + 1, 0));
  }

  private static Instant at(int minute, int second) {
    return START.plus(Duration.ofMinutes(minute)).plusSeconds(second);
  }
}
