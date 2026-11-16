package com.tihomir.t212.tradingbot.bot.tickersettings;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.exception.TickerNotTradedException;
import com.tihomir.t212.tradingbot.order.gateway.VirtualTradingGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** The settings and budget each followed ticker trades with. */
@Service
public class TickerSettingsService {

  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;
  private static final int BUDGET_SCALE = 2;

  private final BrokerRegistryService brokers;
  private final AlgorithmRegistryService algorithms;
  private final AlgorithmSettingsService settingsService;
  private final TickerSettingsStore store;

  public TickerSettingsService(
      BrokerRegistryService brokers,
      AlgorithmRegistryService algorithms,
      AlgorithmSettingsService settingsService,
      TickerSettingsStore store) {
    this.brokers = brokers;
    this.algorithms = algorithms;
    this.settingsService = settingsService;
    this.store = store;
  }

  /** Every followed ticker, in the broker's order, with its settings when it has some. */
  public TickerSettingsList getTickerSettings(String broker) {
    TickerSettingsList assigned = store.getAssignedSettings(broker);
    return new TickerSettingsList(
        broker(broker).tickers().tickers().stream()
            .map(ticker -> new TickerSettings(ticker, assigned.assignedTo(ticker)))
            .toList());
  }

  /**
   * Assigns settings picked by hand. Without a budget the ticker keeps its own, or gets an equal
   * share of the free USDT. A previous auto-tune result stays the one to beat.
   *
   * @throws TickerNotTradedException if the broker doesn't follow the ticker
   */
  public TickerSettings assignSettings(
      String broker, String ticker, AlgorithmSettingsRequest request, Optional<BigDecimal> budget) {
    TickerSettingsList current = getTickerSettings(broker);
    if (current.tickers().stream().noneMatch(settings -> settings.ticker().equals(ticker))) {
      throw new TickerNotTradedException(broker, ticker);
    }
    AlgorithmSettings settings = algorithms.get(request.algorithmType()).resolveSettings(request);
    long algorithmId = settingsService.saveSettings(settings);
    Optional<AssignedSettings> previous = current.assignedTo(ticker);
    BigDecimal chosenBudget =
        budget
            .or(() -> previous.flatMap(AssignedSettings::budget))
            .orElseGet(() -> equalShareOfFreeCash(broker, current.tickers().size()));
    AssignedSettings assigned =
        new AssignedSettings(
            algorithmId,
            settings,
            Optional.of(chosenBudget),
            SettingsSource.MANUAL,
            previous.flatMap(AssignedSettings::tunedRunId));
    store.saveAssignedSettings(broker, ticker, assigned);
    return new TickerSettings(ticker, Optional.of(assigned));
  }

  private BigDecimal equalShareOfFreeCash(String broker, int tickerCount) {
    BigDecimal freeCash =
        broker(broker).tradingGateway().accountBalances().balances().stream()
            .filter(balance -> balance.asset().equals(QUOTE_ASSET))
            .map(AssetBalance::free)
            .findFirst()
            .orElse(BigDecimal.ZERO);
    return freeCash.divide(BigDecimal.valueOf(tickerCount), BUDGET_SCALE, RoundingMode.DOWN);
  }

  private Broker broker(String broker) {
    return brokers.get(broker).orElseThrow(() -> new BrokerNotFoundException(broker));
  }
}
