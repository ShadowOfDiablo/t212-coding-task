package com.tihomir.t212.tradingbot.bot.tickersettings;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.exception.TickerSettingsMissingException;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.exception.TickerNotTradedException;
import com.tihomir.t212.tradingbot.order.gateway.VirtualTradingGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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

  /**
   * Every followed ticker, in the broker's order, with its settings when it has some. Settings
   * stored before budgets existed get an equal share of the free USDT.
   */
  public TickerSettingsList getTickerSettings(String broker) {
    TickerSettingsList assigned = store.getAssignedSettings(broker);
    List<String> tickers = broker(broker).tickers().tickers();
    return new TickerSettingsList(
        tickers.stream()
            .map(ticker -> new TickerSettings(ticker, assigned.assignedTo(ticker)))
            .map(settings -> withBudget(broker, settings, tickers.size()))
            .toList());
  }

  /**
   * @throws TickerSettingsMissingException if the ticker has no settings yet
   */
  public AssignedSettings getAssignedSettings(String broker, String ticker) {
    return getTickerSettings(broker)
        .assignedTo(ticker)
        .orElseThrow(() -> new TickerSettingsMissingException(broker, ticker));
  }

  /** The ticker's money after a run: it keeps its profit or loss for the next one. */
  public void updateBudget(String broker, String ticker, BigDecimal budget) {
    AssignedSettings current = getAssignedSettings(broker, ticker);
    store.saveAssignedSettings(
        broker,
        ticker,
        new AssignedSettings(
            current.algorithmId(),
            current.settings(),
            Optional.of(budget.setScale(BUDGET_SCALE, RoundingMode.DOWN)),
            current.source(),
            current.tunedRunId()));
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

  private TickerSettings withBudget(String broker, TickerSettings settings, int tickerCount) {
    Optional<AssignedSettings> assigned = settings.assigned();
    if (assigned.isEmpty() || assigned.get().budget().isPresent()) {
      return settings;
    }
    AssignedSettings current = assigned.get();
    return new TickerSettings(
        settings.ticker(),
        Optional.of(
            new AssignedSettings(
                current.algorithmId(),
                current.settings(),
                Optional.of(equalShareOfFreeCash(broker, tickerCount)),
                current.source(),
                current.tunedRunId())));
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
