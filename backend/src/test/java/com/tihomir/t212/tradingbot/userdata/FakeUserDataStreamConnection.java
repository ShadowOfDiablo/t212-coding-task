package com.tihomir.t212.tradingbot.userdata;

import java.util.ArrayList;
import java.util.List;

public final class FakeUserDataStreamConnection implements UserDataStreamConnection {

  private final List<String> sentMessages = new ArrayList<>();
  private boolean closed;

  @Override
  public synchronized void sendText(String message) {
    sentMessages.add(message);
  }

  @Override
  public synchronized boolean isOpen() {
    return !closed;
  }

  @Override
  public synchronized void close() {
    closed = true;
  }

  public synchronized List<String> sentMessages() {
    return List.copyOf(sentMessages);
  }
}
