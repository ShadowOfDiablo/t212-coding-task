package com.tihomir.t212.tradingbot.broker;

import com.tihomir.t212.tradingbot.order.exception.TradingNotConfiguredException;

public final class MissingKeyRequestSigner implements RequestSigner {

  @Override
  public String sign(String payload) {
    throw new TradingNotConfiguredException();
  }
}
