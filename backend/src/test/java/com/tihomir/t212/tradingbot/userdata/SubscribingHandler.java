package com.tihomir.t212.tradingbot.userdata;

import java.util.List;

final class SubscribingHandler implements UserDataStreamHandler {

  private final List<String> received;

  SubscribingHandler(List<String> received) {
    this.received = received;
  }

  @Override
  public void onConnected(UserDataStreamConnection connection) {
    connection.sendText("{\"method\":\"subscribe\"}");
  }

  @Override
  public void onMessage(String message) {
    received.add(message);
  }
}
