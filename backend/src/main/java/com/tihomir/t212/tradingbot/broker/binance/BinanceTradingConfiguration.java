package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.account.AccountResetStore;
import com.tihomir.t212.tradingbot.broker.MissingKeyRequestSigner;
import com.tihomir.t212.tradingbot.broker.RequestSigner;
import com.tihomir.t212.tradingbot.order.balance.AccountBalanceSynchronizer;
import com.tihomir.t212.tradingbot.order.balance.BalanceCachingTradingGateway;
import com.tihomir.t212.tradingbot.order.balance.StartingBalances;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BinanceTradingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(BinanceTradingConfiguration.class);

  private static final BigDecimal DEMO_STARTING_AMOUNT_PER_ASSET = BigDecimal.valueOf(5_000);

  /** What Binance's demo account holds after a reset on its website. */
  static final StartingBalances DEMO_STARTING_BALANCES =
      new StartingBalances(
          Map.of("USDT", DEMO_STARTING_AMOUNT_PER_ASSET, "USDC", DEMO_STARTING_AMOUNT_PER_ASSET));

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
      BinanceTradingGateway tradingGateway,
      BinanceConnectionProperties connectionProperties,
      AccountResetStore accountResetStore) {
    String broker = connectionProperties.name();
    return new BalanceCachingTradingGateway(
        tradingGateway,
        DEMO_STARTING_BALANCES,
        resetAt -> accountResetStore.insertAccountReset(broker, resetAt));
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
