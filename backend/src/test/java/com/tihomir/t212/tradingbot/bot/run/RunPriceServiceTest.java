package com.tihomir.t212.tradingbot.bot.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RunPriceServiceTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final int FEW_CANDLES = 5;
  private static final int MANY_CANDLES = 2_500;

  @Test
  void keepsEveryCandleWhenThereAreFewAndTheirClosingPrices() {
    List<PricePoint> points = RunPriceService.thinned(candles(FEW_CANDLES));

    assertThat(points).hasSize(FEW_CANDLES);
    assertThat(points.getFirst()).isEqualTo(new PricePoint(START, BigDecimal.ZERO));
  }

  @Test
  void thinsManyCandlesEvenlyAndKeepsTheFirstAndLast() {
    List<CandleUpdate> candles = candles(MANY_CANDLES);

    List<PricePoint> points = RunPriceService.thinned(candles);

    assertThat(points).hasSizeLessThanOrEqualTo(RunPriceService.MAX_PRICE_POINTS);
    assertThat(points.getFirst().time()).isEqualTo(candles.getFirst().openTime());
    assertThat(points.getLast().time()).isEqualTo(candles.getLast().openTime());
  }

  @Test
  void answersNoPointsForNoCandles() {
    assertThat(RunPriceService.thinned(List.of())).isEmpty();
  }

  private static List<CandleUpdate> candles(int count) {
    return IntStream.range(0, count).mapToObj(RunPriceServiceTest::candle).toList();
  }

  private static CandleUpdate candle(int index) {
    Instant openTime = START.plus(CandleInterval.ONE_MINUTE.duration().multipliedBy(index));
    BigDecimal close = BigDecimal.valueOf(index);
    return new CandleUpdate(
        "BTCUSDT",
        CandleInterval.ONE_MINUTE,
        openTime,
        openTime.plus(CandleInterval.ONE_MINUTE.duration()),
        close,
        close,
        close,
        close,
        BigDecimal.ONE,
        true,
        openTime);
  }
}
