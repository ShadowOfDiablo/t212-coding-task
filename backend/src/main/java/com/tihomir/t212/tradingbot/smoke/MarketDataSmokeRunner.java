package com.tihomir.t212.tradingbot.smoke;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.marketdata.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.CandleFeederStatistics;
import com.tihomir.t212.tradingbot.marketdata.CandleUpdate;
import com.tihomir.t212.tradingbot.marketdata.TickerPriceReader;
import java.time.Duration;
import java.time.Instant;
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
        "{} statistics: messages={} parseErrors={} dropped={} queue={} parse avg/max={}/{}us"
            + " lag avg/max={}/{}ms connections={}/{} open",
        broker.name(),
        statistics.messages(),
        statistics.parseErrors(),
        statistics.droppedUpdates(),
        statistics.queueDepth(),
        statistics.avgParseMicros(),
        statistics.maxParseMicros(),
        statistics.avgLagMillis(),
        statistics.maxLagMillis(),
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
