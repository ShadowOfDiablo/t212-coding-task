package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.MissingKeyRequestSigner;
import com.tihomir.t212.tradingbot.broker.RequestSigner;
import com.tihomir.t212.tradingbot.order.balance.AccountBalanceSynchronizer;
import com.tihomir.t212.tradingbot.order.balance.BalanceCachingTradingGateway;
import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BinanceTradingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(BinanceTradingConfiguration.class);

  @Bean
  RequestSigner binanceRequestSigner(BinanceTradingProperties tradingProperties) {
    if (!tradingProperties.credentialsConfigured()) {
      log.info("Binance: no demo API key configured, trading is off");
      return new MissingKeyRequestSigner();
    }
    log.info("Binance: trading on the demo account at {}", tradingProperties.restUrl());
    return BinanceRequestSigner.fromPemFile(Path.of(tradingProperties.privateKeyPath()));
  }

  @Bean
  BalanceCachingTradingGateway binanceBalanceCachingTradingGateway(
      BinanceTradingGateway tradingGateway) {
    return new BalanceCachingTradingGateway(tradingGateway);
  }

  @Bean
  AccountBalanceSynchronizer binanceAccountBalanceSynchronizer(
      BalanceCachingTradingGateway tradingGateway,
      BinanceUserDataStreamProperties streamProperties) {
    return new AccountBalanceSynchronizer(
        tradingGateway,
        streamProperties.balanceSyncInterval(),
        streamProperties.balanceSyncRetryDelay());
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
