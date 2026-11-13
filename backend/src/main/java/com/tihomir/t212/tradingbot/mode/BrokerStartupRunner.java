package com.tihomir.t212.tradingbot.mode;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.FollowedTickerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BrokerStartupRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BrokerStartupRunner.class);

  private final BrokerRegistryService brokers;
  private final BotModeService botModeService;
  private final FollowedTickerService followedTickers;

  public BrokerStartupRunner(
      BrokerRegistryService brokers,
      BotModeService botModeService,
      FollowedTickerService followedTickers) {
    this.brokers = brokers;
    this.botModeService = botModeService;
    this.followedTickers = followedTickers;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (Broker broker : brokers.all()) {
      followedTickers.restoreFollowedTickers(broker);
      log.info(
          "{}: {} mode, streaming {} live",
          broker.name(),
          botModeService.botMode(),
          broker.tickers().tickers());
      broker.candleFeeder().start();
      startAccountUpdates(broker);
    }
  }

  private static void startAccountUpdates(Broker broker) {
    try {
      broker.startAccountUpdates();
    } catch (RuntimeException e) {
      log.error(
          "{}: starting account updates failed, balances load on the first read", broker.name(), e);
    }
  }
}
