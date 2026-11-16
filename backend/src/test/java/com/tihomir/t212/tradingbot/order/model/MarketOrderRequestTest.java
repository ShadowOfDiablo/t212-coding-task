package com.tihomir.t212.tradingbot.order.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarketOrderRequestTest {

  @Test
  void refusesAZeroOrNegativeAmount() {
    assertThatThrownBy(() -> request("BTCUSDT", OrderSide.BUY, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amount");
    assertThatThrownBy(() -> request("BTCUSDT", OrderSide.BUY, new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesMissingFields() {
    assertThatThrownBy(() -> request(" ", OrderSide.BUY, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ticker");
    assertThatThrownBy(() -> request("BTCUSDT", null, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("side");
    assertThatThrownBy(() -> new MarketOrderRequest("BTCUSDT", OrderSide.BUY, BigDecimal.ONE, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amountType");
  }

  private static MarketOrderRequest request(String ticker, OrderSide side, BigDecimal amount) {
    return new MarketOrderRequest(ticker, side, amount, OrderAmountType.QUOTE_AMOUNT);
  }
}
