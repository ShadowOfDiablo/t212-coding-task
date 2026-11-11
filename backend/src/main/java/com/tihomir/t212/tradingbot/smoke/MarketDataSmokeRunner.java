package com.tihomir.t212.tradingbot.smoke;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.marketdata.candlefeed.CandleFeeder;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceReader;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.marketdata.update.CandleUpdate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
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
  private final ConfigurableApplicationContext context;

  public MarketDataSmokeRunner(
      BrokerRegistryService brokers,
      @Value("${smoke.duration:30s}") Duration duration,
      ConfigurableApplicationContext context) {
    this.brokers = brokers;
    this.duration = duration;
    this.context = context;
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
        logPrices(broker);
      }
    }

    brokers.all().forEach(broker -> broker.candleFeeder().stop());
    log.info("Smoke run finished after {}", duration);
    SpringApplication.exit(context);
  }

  private static void logRecentCandleHistory(Broker broker) {
    String ticker = broker.tickers().first();
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
