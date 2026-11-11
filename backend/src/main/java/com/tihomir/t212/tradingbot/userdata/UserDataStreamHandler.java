package com.tihomir.t212.tradingbot.userdata;

public interface UserDataStreamHandler {

  void onConnected(UserDataStreamConnection connection);

  void onMessage(String message);
}
