package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.order.exception.ExchangeUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Follows more tickers than the configured ones. A followed ticker is checked on the exchange,
 * stored, and then streamed like the configured ones; it is followed again after a restart.
 */
@Service
public class FollowedTickerService {

  private static final Logger log = LoggerFactory.getLogger(FollowedTickerService.class);
  private static final Duration CHECKED_HISTORY = Duration.ofHours(1);

  private final BrokerRegistryService brokers;
  private final FollowedTickerStore store;
  private final Clock clock;

  public FollowedTickerService(
      BrokerRegistryService brokers, FollowedTickerStore store, Clock clock) {
    this.brokers = brokers;
    this.store = store;
    this.clock = clock;
  }

  /**
   * @throws UnknownTickerException if the exchange doesn't list the ticker
   * @throws ExchangeUnavailableException if the exchange can't be asked
   */
  public synchronized TickerList followTicker(String brokerName, String ticker) {
    Broker broker = broker(brokerName);
    if (broker.tickers().contains(ticker)) {
      return broker.tickers();
    }
    checkListedOnExchange(broker, ticker);
    store.insertFollowedTicker(brokerName, ticker, clock.instant());
    broker.addTicker(ticker);
    log.info("{}: now following {}", brokerName, ticker);
    return broker.tickers();
  }

  /**
   * Called at startup, before the streams open. Stores the configured tickers as followed first, so
   * the database lists every followed ticker; one already stored keeps its time. A failure is
   * logged and startup goes on.
   */
  public void restoreFollowedTickers(Broker broker) {
    Instant now = clock.instant();
    try {
      broker.tickers().forEach(ticker -> store.insertFollowedTicker(broker.name(), ticker, now));
      store.getFollowedTickers(broker.name()).forEach(broker::addTicker);
    } catch (DataAccessException e) {
      log.error(
          "{}: could not store or read the followed tickers: {}", broker.name(), e.getMessage(), e);
    }
  }

  private void checkListedOnExchange(Broker broker, String ticker) {
    Instant now = clock.instant();
    try {
      broker
          .candleFeeder()
          .historyPage(ticker, broker.candleFeeder().interval(), now.minus(CHECKED_HISTORY), now);
    } catch (HttpClientErrorException e) {
      throw new UnknownTickerException(broker.name(), ticker, e);
    } catch (RuntimeException e) {
      throw new ExchangeUnavailableException(
          "Could not check " + ticker + " on " + broker.name() + ": " + e.getMessage(), e);
    }
  }

  private Broker broker(String brokerName) {
    return brokers.get(brokerName).orElseThrow(() -> new BrokerNotFoundException(brokerName));
  }
}
