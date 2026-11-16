package com.tihomir.t212.tradingbot.account.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountValueHistoryTest {

  @Test
  void dropsTheOldestPointPastTheLimit() {
    AccountValueHistory history = new AccountValueHistory();

    for (int second = 0; second <= AccountValueHistory.MAX_POINTS; second++) {
      history.addPoint(new AccountValuePoint(Instant.ofEpochSecond(second), BigDecimal.ONE));
    }

    assertThat(history.points().points()).hasSize(AccountValueHistory.MAX_POINTS);
    assertThat(history.points().points().getFirst().time()).isEqualTo(Instant.ofEpochSecond(1));
  }
}
