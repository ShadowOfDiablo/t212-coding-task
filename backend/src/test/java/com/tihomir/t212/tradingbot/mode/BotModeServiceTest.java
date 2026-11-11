package com.tihomir.t212.tradingbot.mode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BotModeServiceTest {

  private final BotModeService botModeService = new BotModeService(BotMode.TRADING);

  @Test
  void startsInTheConfiguredMode() {
    assertThat(botModeService.botMode()).isEqualTo(BotMode.TRADING);
  }

  @Test
  void switchesModeWithoutARestart() {
    botModeService.setBotMode(BotMode.TRAINING);

    assertThat(botModeService.botMode()).isEqualTo(BotMode.TRAINING);
  }

  @Test
  void refusesAMissingModeAndKeepsTheCurrentOne() {
    assertThatThrownBy(() -> botModeService.setBotMode(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(botModeService.botMode()).isEqualTo(BotMode.TRADING);
  }
}
