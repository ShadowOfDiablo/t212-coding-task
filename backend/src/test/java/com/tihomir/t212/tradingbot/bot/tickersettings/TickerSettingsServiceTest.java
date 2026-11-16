package com.tihomir.t212.tradingbot.bot.tickersettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingsService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.FakeAlgorithmStore;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.ScriptedTestAlgorithm;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.order.exception.TickerNotTradedException;
import com.tihomir.t212.tradingbot.order.gateway.FakeExchangeTradingGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TickerSettingsServiceTest {

  private static final String BROKER = "binance";
  private static final String BTC = FakeBroker.TRADED_TICKER;
  private static final String ETH = "ETHUSDT";
  private static final int TIME_PERIOD = 20;
  private static final BigDecimal OWN_BUDGET = new BigDecimal("1500");
  private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

  private final FakeBroker broker = new FakeBroker(BROKER, new FakeExchangeTradingGateway());
  private final FakeTickerSettingsStore store = new FakeTickerSettingsStore();
  private final TickerSettingsService service =
      new TickerSettingsService(
          new BrokerRegistryService(List.of(broker)),
          new AlgorithmRegistryService(List.of(new ScriptedTestAlgorithm(List.of(), () -> {}))),
          new AlgorithmSettingsService(new FakeAlgorithmStore(1), CLOCK),
          store);

  @BeforeEach
  void followASecondTicker() {
    broker.addTicker(ETH);
  }

  @Test
  void listsEveryFollowedTickerWithoutSettingsAtFirst() {
    assertThat(service.getTickerSettings(BROKER).tickers())
        .containsExactly(
            new TickerSettings(BTC, Optional.empty()), new TickerSettings(ETH, Optional.empty()));
  }

  @Test
  void assignsSettingsByHandWithAnEqualShareOfTheFreeCash() {
    TickerSettings assigned = service.assignSettings(BROKER, BTC, settings(), Optional.empty());

    AssignedSettings settings = assigned.assigned().orElseThrow();
    assertThat(settings.source()).isEqualTo(SettingsSource.MANUAL);
    assertThat(settings.settings().timePeriod()).isEqualTo(TIME_PERIOD);
    assertThat(settings.budget())
        .hasValueSatisfying(budget -> assertThat(budget).isEqualByComparingTo("50"));
    assertThat(service.getTickerSettings(BROKER).assignedTo(BTC)).contains(settings);
    assertThat(service.getTickerSettings(BROKER).assignedTo(ETH)).isEmpty();
  }

  @Test
  void keepsTheTickersBudgetUnlessANewOneIsGiven() {
    service.assignSettings(BROKER, BTC, settings(), Optional.of(OWN_BUDGET));

    TickerSettings reassigned = service.assignSettings(BROKER, BTC, settings(), Optional.empty());

    assertThat(reassigned.assigned().orElseThrow().budget()).contains(OWN_BUDGET);
  }

  @Test
  void refusesATickerThatIsNotFollowed() {
    assertThatThrownBy(
            () -> service.assignSettings(BROKER, "DOGEUSDT", settings(), Optional.empty()))
        .isInstanceOf(TickerNotTradedException.class);
  }

  private static AlgorithmSettingsRequest settings() {
    return new AlgorithmSettingsRequest(
        AlgorithmType.SIMPLE_MOVING_AVERAGE,
        CandleInterval.ONE_MINUTE,
        true,
        Map.of(TradingAlgorithm.TIME_PERIOD, BigDecimal.valueOf(TIME_PERIOD)));
  }
}
