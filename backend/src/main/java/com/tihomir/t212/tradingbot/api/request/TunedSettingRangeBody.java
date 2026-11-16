package com.tihomir.t212.tradingbot.api.request;

import com.tihomir.t212.tradingbot.bot.tuning.TunedSettingRange;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TunedSettingRangeBody(
    @NotBlank String name, @NotNull BigDecimal minimum, @NotNull BigDecimal maximum) {

  @AssertTrue(message = "a tuned setting's minimum is at most its maximum")
  public boolean isMinimumAtMostMaximum() {
    return minimum == null || maximum == null || minimum.compareTo(maximum) <= 0;
  }

  TunedSettingRange toTunedSettingRange() {
    return new TunedSettingRange(name, minimum, maximum);
  }
}
