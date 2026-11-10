package com.tihomir.t212.tradingbot.mode;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("trading")
public class LiveMarketDataStarter implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LiveMarketDataStarter.class);

  private final BrokerRegistryService brokers;

  public LiveMarketDataStarter(BrokerRegistryService brokers) {
    this.brokers = brokers;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (Broker broker : brokers.all()) {
      log.info(
          "{}: trading mode, streaming {} live, trading {}",
          broker.name(),
          broker.tickers(),
          tradingState(broker));
      broker.candleFeeder().start();
    }
  }

  private static String tradingState(Broker broker) {
    if (broker.tradingGateway().isPresent()) {
      return "on";
    }
    return "off (no API key)";
  }
}
