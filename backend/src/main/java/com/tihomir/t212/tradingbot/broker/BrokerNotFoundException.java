package com.tihomir.t212.tradingbot.broker;

public class BrokerNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public BrokerNotFoundException(String brokerName) {
    super("Unknown broker: " + brokerName);
  }
}
