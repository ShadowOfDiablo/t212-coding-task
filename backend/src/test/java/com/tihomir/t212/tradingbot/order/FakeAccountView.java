package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.util.Optional;

record FakeAccountView(
    String ticker, BigDecimal cash, BigDecimal baseQuantity, Optional<BigDecimal> entryPrice)
    implements AccountView {

  static FakeAccountView withCash(String cash) {
    return new FakeAccountView("BTCUSDT", new BigDecimal(cash), BigDecimal.ZERO, Optional.empty());
  }

  static FakeAccountView holding(String quantity, String entryPrice) {
    return new FakeAccountView(
        "BTCUSDT",
        BigDecimal.ZERO,
        new BigDecimal(quantity),
        Optional.of(new BigDecimal(entryPrice)));
  }
}
