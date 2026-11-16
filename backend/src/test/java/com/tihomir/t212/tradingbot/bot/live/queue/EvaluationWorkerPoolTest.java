package com.tihomir.t212.tradingbot.bot.live.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.exception.OrderStatusUnknownException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EvaluationWorkerPoolTest {

  private static final String BTC = "BTCUSDT";
  private static final String ETH = "ETHUSDT";
  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  private static final int NO_WORKERS = 0;
  private static final int TWO_WORKERS = 2;
  private static final int TWO_EVALUATIONS = 2;
  private static final int SAME_TICKER_PRICES = 200;
  private static final ExchangeRejectedRequestException REJECTED =
      new ExchangeRejectedRequestException(-2010, "Not enough USDT");

  private final LiveEvaluationQueue queue = new LiveEvaluationQueue("binance");
  private final FakePriceMessageHandler btcHandler = new FakePriceMessageHandler();

  @Test
  void evaluatesAPriceAndFinishesIt() {
    queue.registerHandler(BTC, btcHandler);
    EvaluationWorkerPool pool = new EvaluationWorkerPool(queue, NO_WORKERS);

    pool.handleMessage(price(BTC));

    assertThat(btcHandler.evaluated()).containsExactly(price(BTC));
    assertThat(btcHandler.finishedCount()).isEqualTo(1);
  }

  @Test
  void putsARejectedPriceBackAtTheFrontUntilItsTriesRunOut() throws InterruptedException {
    queue.registerHandler(BTC, btcHandler);
    btcHandler.failEvaluations(REJECTED, EvaluationRetryPolicy.MAX_ATTEMPTS);
    EvaluationWorkerPool pool = new EvaluationWorkerPool(queue, NO_WORKERS);
    queue.offerLast(price(ETH));

    pool.handleMessage(price(BTC));
    PriceMessage retry = queue.takeFirst();
    pool.handleMessage(retry);
    pool.handleMessage(queue.takeFirst());

    assertThat(retry.attempt()).isEqualTo(2);
    assertThat(btcHandler.failures()).containsExactly(REJECTED);
    assertThat(btcHandler.finishedCount()).isEqualTo(1);
    assertThat(queue.takeFirst().ticker()).isEqualTo(ETH);
  }

  @Test
  void neverRetriesAnOrderWhoseStatusIsUnknown() {
    queue.registerHandler(BTC, btcHandler);
    OrderStatusUnknownException unknown = new OrderStatusUnknownException("tb-1", "no answer");
    btcHandler.failEvaluations(unknown, 1);
    EvaluationWorkerPool pool = new EvaluationWorkerPool(queue, NO_WORKERS);

    pool.handleMessage(price(BTC));

    assertThat(btcHandler.failures()).containsExactly(unknown);
    assertThat(btcHandler.finishedCount()).isEqualTo(1);
  }

  @Test
  void dropsAPriceOfATickerWithoutABot() {
    EvaluationWorkerPool pool = new EvaluationWorkerPool(queue, NO_WORKERS);

    pool.handleMessage(price(BTC));

    assertThat(btcHandler.evaluated()).isEmpty();
  }

  @Test
  void evaluatesDifferentTickersAtTheSameTimeOnItsWorkers() {
    CyclicBarrier bothRunning = new CyclicBarrier(TWO_EVALUATIONS);
    FakePriceMessageHandler ethHandler = new FakePriceMessageHandler();
    btcHandler.waitAt(bothRunning);
    ethHandler.waitAt(bothRunning);
    queue.registerHandler(BTC, btcHandler);
    queue.registerHandler(ETH, ethHandler);

    try (EvaluationWorkerPool pool = new EvaluationWorkerPool(queue, TWO_WORKERS)) {
      queue.offerLast(price(BTC));
      queue.offerLast(price(ETH));

      await()
          .until(() -> btcHandler.finishedCount() + ethHandler.finishedCount() == TWO_EVALUATIONS);
    }
    assertThat(btcHandler.failures()).isEmpty();
    assertThat(ethHandler.failures()).isEmpty();
  }

  @Test
  void finishesOnePriceOfATickerBeforeAnyWorkerEvaluatesItsNext() {
    queue.registerHandler(BTC, btcHandler);

    try (EvaluationWorkerPool pool = new EvaluationWorkerPool(queue, TWO_WORKERS)) {
      for (int index = 0; index < SAME_TICKER_PRICES; index++) {
        queue.offerLast(price(BTC));
      }

      await().until(() -> btcHandler.finishedCount() == SAME_TICKER_PRICES);
    }
    assertThat(btcHandler.steps())
        .containsExactlyElementsOf(
            IntStream.range(0, SAME_TICKER_PRICES)
                .mapToObj(index ->
                    List.of(FakePriceMessageHandler.EVALUATED, FakePriceMessageHandler.FINISHED))
                .flatMap(List::stream)
                .toList());
  }

  @Test
  void dropsANewPriceWhenTheQueueIsFull() {
    for (int index = 0; index < LiveEvaluationQueue.CAPACITY; index++) {
      queue.offerLast(price(BTC));
    }

    assertThat(queue.offerLast(price(ETH))).isFalse();
  }

  private static PriceMessage price(String ticker) {
    return PriceMessage.firstAttempt(ticker, BigDecimal.ONE, NOW);
  }
}
