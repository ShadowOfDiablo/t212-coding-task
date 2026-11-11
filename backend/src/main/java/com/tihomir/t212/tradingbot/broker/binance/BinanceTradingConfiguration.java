package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.MissingKeyRequestSigner;
import com.tihomir.t212.tradingbot.broker.RequestSigner;
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
  Clock clock() {
    return Clock.systemUTC();
  }
}
