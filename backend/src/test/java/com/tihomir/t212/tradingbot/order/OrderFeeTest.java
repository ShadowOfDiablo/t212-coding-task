package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderFeeTest {

  private static final long FIRST_TRADE_ID = 1;
  private static final long SECOND_TRADE_ID = 2;

  @Test
  void addsUpTheFeesOfFillsPaidInOneCurrency() {
    assertThat(
            OrderFee.of(
                List.of(fill(FIRST_TRADE_ID, "0.1", "USDT"), fill(SECOND_TRADE_ID, "0.2", "USDT"))))
        .hasValueSatisfying(fee -> {
          assertThat(fee.amount()).isEqualByComparingTo("0.3");
          assertThat(fee.currency()).isEqualTo("USDT");
        });
  }

  @Test
  void hasNoFeeForFillsPaidInMoreThanOneCurrencyOrForNoFills() {
    assertThat(
            OrderFee.of(
                List.of(fill(FIRST_TRADE_ID, "0.1", "BNB"), fill(SECOND_TRADE_ID, "0.2", "BTC"))))
        .isEmpty();
    assertThat(OrderFee.of(List.of())).isEmpty();
  }

  private static OrderFill fill(long tradeId, String feeAmount, String feeCurrency) {
    return new OrderFill(
        tradeId, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(feeAmount), feeCurrency);
  }
}
