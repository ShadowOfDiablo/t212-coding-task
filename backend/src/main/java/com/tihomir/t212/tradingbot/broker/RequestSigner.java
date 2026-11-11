package com.tihomir.t212.tradingbot.broker;

public interface RequestSigner {

  String sign(String payload);
}
