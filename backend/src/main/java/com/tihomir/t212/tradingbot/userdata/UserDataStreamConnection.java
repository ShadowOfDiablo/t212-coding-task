package com.tihomir.t212.tradingbot.userdata;

public interface UserDataStreamConnection extends AutoCloseable {

  void sendText(String message);

  boolean isOpen();

  @Override
  void close();
}
