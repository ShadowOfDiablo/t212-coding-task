package com.tihomir.t212.tradingbot.account.value;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuote;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.exception.TradingNotConfiguredException;
import com.tihomir.t212.tradingbot.order.gateway.VirtualTradingGateway;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Samples each broker's whole account value every minute, from the cached balances and the last
 * prices, and keeps the history in memory only: it starts again empty after a restart.
 */
@Service
public class AccountValueService {

  static final Duration SAMPLE_INTERVAL = Duration.ofMinutes(1);

  private static final Logger log = LoggerFactory.getLogger(AccountValueService.class);
  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;

  private final BrokerRegistryService brokers;
  private final Clock clock;
  private final Map<String, AccountValueHistory> histories = new ConcurrentHashMap<>();
  private final ScheduledExecutorService sampler =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("account-value-sampler").factory());

  public AccountValueService(BrokerRegistryService brokers, Clock clock) {
    this.brokers = brokers;
    this.clock = clock;
  }

  @PostConstruct
  void startSampling() {
    sampler.scheduleWithFixedDelay(
        this::recordAccountValues, 0, SAMPLE_INTERVAL.toSeconds(), TimeUnit.SECONDS);
  }

  @PreDestroy
  void stopSampling() {
    sampler.shutdownNow();
  }

  /**
   * @throws BrokerNotFoundException for an unknown broker
   */
  public AccountValuePointList getAccountValuePoints(String broker) {
    brokers.get(broker).orElseThrow(() -> new BrokerNotFoundException(broker));
    return Optional.ofNullable(histories.get(broker))
        .map(AccountValueHistory::points)
        .orElseGet(() -> new AccountValuePointList(List.of()));
  }

  /** Runs on the sampler, so it never throws: a broker that fails is logged and skipped. */
  void recordAccountValues() {
    brokers.all().forEach(this::recordAccountValueOrLog);
  }

  private void recordAccountValueOrLog(Broker broker) {
    try {
      AccountValuePoint point = new AccountValuePoint(clock.instant(), accountValue(broker));
      histories.computeIfAbsent(broker.name(), name -> new AccountValueHistory()).addPoint(point);
    } catch (TradingNotConfiguredException e) {
      log.debug("{}: no account to value, trading is not configured", broker.name());
    } catch (RuntimeException e) {
      log.warn("{}: sampling the account value failed: {}", broker.name(), e.toString());
    }
  }

  /** USDT counts at 1; any other asset at its USDT last price, or not at all without one. */
  private static BigDecimal accountValue(Broker broker) {
    return broker.tradingGateway().accountBalances().balances().stream()
        .map(balance -> assetValue(broker, balance))
        .flatMap(Optional::stream)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static Optional<BigDecimal> assetValue(Broker broker, AssetBalance balance) {
    BigDecimal quantity = balance.free().add(balance.locked());
    if (balance.asset().equals(QUOTE_ASSET)) {
      return Optional.of(quantity);
    }
    return broker
        .tickerPrices()
        .get(balance.asset() + QUOTE_ASSET)
        .map(TickerQuote::lastPrice)
        .map(quantity::multiply);
  }
}
