package com.tihomir.t212.tradingbot.mode;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.order.TradingNotConfiguredException;
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

  public BrokerStartupRunner(BrokerRegistryService brokers, BotModeService botModeService) {
    this.brokers = brokers;
    this.botModeService = botModeService;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (Broker broker : brokers.all()) {
      log.info(
          "{}: {} mode, streaming {} live",
          broker.name(),
          botModeService.botMode(),
          broker.tickers());
      broker.candleFeeder().start();
      loadAccountBalances(broker);
    }
  }

  private static void loadAccountBalances(Broker broker) {
    try {
      log.info(
          "{}: loaded {} account balances",
          broker.name(),
          broker.tradingGateway().accountBalances().size());
    } catch (TradingNotConfiguredException e) {
      log.info("{}: no account balances to load, trading is off", broker.name());
    } catch (RuntimeException e) {
      log.warn("{}: could not load account balances, retrying on the first read", broker.name(), e);
    }
  }
}
