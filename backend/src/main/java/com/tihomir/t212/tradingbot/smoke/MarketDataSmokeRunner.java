package com.tihomir.t212.tradingbot.smoke;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.marketdata.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.CandleFeederStatistics;
import com.tihomir.t212.tradingbot.marketdata.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.CandleUpdate;
import com.tihomir.t212.tradingbot.marketdata.TickerPriceReader;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("smoke")
public class MarketDataSmokeRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MarketDataSmokeRunner.class);
  private static final Duration REPORT_EVERY = Duration.ofSeconds(5);
  private static final Duration HISTORY_WINDOW = Duration.ofMinutes(20);

  private final BrokerRegistryService brokers;
  private final Duration duration;

  public MarketDataSmokeRunner(
      BrokerRegistryService brokers, @Value("${smoke.duration:30s}") Duration duration) {
    this.brokers = brokers;
    this.duration = duration;
  }

  @Override
  public void run(ApplicationArguments args) throws InterruptedException {
    for (Broker broker : brokers.all()) {
      CandleFeeder feeder = broker.candleFeeder();
      feeder.addListener(update -> logCandleUpdate(broker, update));
      logPrices(broker);
      logRecentCandleHistory(broker);
      feeder.start();
    }

    Instant end = Instant.now().plus(duration);
    while (Instant.now().isBefore(end)) {
      Thread.sleep(REPORT_EVERY.toMillis());
      for (Broker broker : brokers.all()) {
        logStatistics(broker);
        logPrices(broker);
      }
    }

    brokers.all().forEach(broker -> broker.candleFeeder().stop());
    log.info("Smoke run finished after {}", duration);
  }

  private static void logRecentCandleHistory(Broker broker) {
    String ticker = broker.tickers().getFirst();
    Instant to = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant from = to.minus(HISTORY_WINDOW);
    try {
      List<CandleUpdate> candles =
          broker.candleFeeder().history(ticker, CandleInterval.ONE_SECOND, from, to);
      if (candles.isEmpty()) {
        log.warn(
            "{} {} REST history: no candles between {} and {}", broker.name(), ticker, from, to);
        return;
      }
      CandleUpdate last = candles.getLast();
      log.info(
          "{} {} REST history: {} one-second candles from {} to {}, last close={}",
          broker.name(),
          ticker,
          candles.size(),
          candles.getFirst().openTime(),
          last.openTime(),
          last.close());
    } catch (RuntimeException e) {
      log.error("{} {} REST history failed", broker.name(), ticker, e);
    }
  }

  private static void logCandleUpdate(Broker broker, CandleUpdate update) {
    log.info(
        "{} {} close={} closed={} event={}",
        broker.name(),
        update.ticker(),
        update.close(),
        update.closed(),
        update.eventTime());
  }

  private static void logStatistics(Broker broker) {
    CandleFeederStatistics statistics = broker.candleFeeder().statistics();
    log.info(
        "{} statistics: messages={} parseErrors={} dropped={} queue={} parse average/max={}/{}us"
            + " lag average/max={}/{}ms exchangeClockOffset={}ms connections={}/{} open",
        broker.name(),
        statistics.messages(),
        statistics.parseErrors(),
        statistics.droppedUpdates(),
        statistics.queueDepth(),
        statistics.averageParseMicros(),
        statistics.maxParseMicros(),
        statistics.averageLagMillis(),
        statistics.maxLagMillis(),
        statistics.exchangeClockOffsetMillis(),
        statistics.openConnections(),
        statistics.connections());
  }

  private static void logPrices(Broker broker) {
    TickerPriceReader prices = broker.tickerPrices();
    for (String ticker : prices.tickers()) {
      log.info(
          "{} {} quote={}",
          broker.name(),
          ticker,
          prices.get(ticker).map(Object::toString).orElse("null"));
    }
  }
}
