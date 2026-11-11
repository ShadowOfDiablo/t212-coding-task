package com.tihomir.t212.tradingbot.broker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public final class BrokerRegistryService {

  private final Map<String, Broker> brokers;

  public BrokerRegistryService(List<Broker> brokers) {
    Map<String, Broker> byName = new LinkedHashMap<>();
    for (Broker broker : brokers) {
      if (byName.putIfAbsent(broker.name(), broker) != null) {
        throw new IllegalStateException("Duplicate broker name: " + broker.name());
      }
    }
    this.brokers = Collections.unmodifiableMap(byName);
  }

  public Optional<Broker> get(String name) {
    return Optional.ofNullable(brokers.get(name));
  }

  public BrokerList all() {
    return new BrokerList(List.copyOf(brokers.values()));
  }
}
