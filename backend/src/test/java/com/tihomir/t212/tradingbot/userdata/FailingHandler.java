package com.tihomir.t212.tradingbot.userdata;

final class FailingHandler implements UserDataStreamHandler {

  @Override
  public void onConnected(UserDataStreamConnection connection) {
    throw new IllegalStateException("subscribing failed");
  }

  @Override
  public void onMessage(String message) {
    throw new IllegalStateException("handler failed");
  }
}
