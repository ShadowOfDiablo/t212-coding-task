package com.tihomir.t212.tradingbot.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class BrokerRegistryServiceTest {

  @Test
  void findsBrokersByName() {
    Broker binance = new FakeBroker("binance");
    Broker kraken = new FakeBroker("kraken");

    BrokerRegistryService registry = new BrokerRegistryService(List.of(binance, kraken));

    assertThat(registry.get("binance")).containsSame(binance);
    assertThat(registry.get("kraken")).containsSame(kraken);
    assertThat(registry.get("other")).isEmpty();
    assertThat(registry.all()).containsExactly(binance, kraken);
  }

  @Test
  void rejectsTwoBrokersWithTheSameName() {
    List<Broker> brokers = List.of(new FakeBroker("binance"), new FakeBroker("binance"));

    assertThatThrownBy(() -> new BrokerRegistryService(brokers))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("binance");
  }

  @Test
  void brokerStartsWithNullPricesForItsTickers() {
    Broker broker = new FakeBroker("binance");

    assertThat(broker.tickerPrices().tickers()).containsExactly("BTCUSDT");
    assertThat(broker.tickerPrices().get("BTCUSDT")).isEmpty();
    assertThat(broker.candleFeeder()).isSameAs(broker.candleFeeder());
  }
}
