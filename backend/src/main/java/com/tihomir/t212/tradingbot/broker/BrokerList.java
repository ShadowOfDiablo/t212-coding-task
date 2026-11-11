package com.tihomir.t212.tradingbot.broker;

import java.util.Iterator;
import java.util.List;

public record BrokerList(List<Broker> brokers) implements Iterable<Broker> {

  public BrokerList {
    brokers = List.copyOf(brokers);
  }

  @Override
  public Iterator<Broker> iterator() {
    return brokers.iterator();
  }
}
