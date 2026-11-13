package com.tihomir.t212.tradingbot.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.bot.LiveBotRunningException;
import com.tihomir.t212.tradingbot.bot.TradingBotService;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotModeController.class)
class BotModeControllerTest {

  private static final String BOT_MODE = "/api/bot/mode";

  @Autowired MockMvc mockMvc;

  @MockitoBean BotModeService botModeService;

  @MockitoBean TradingBotService tradingBotService;

  @Test
  void readsTheCurrentMode() throws Exception {
    when(botModeService.botMode()).thenReturn(BotMode.TRADING);

    mockMvc
        .perform(get(BOT_MODE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("TRADING"));
  }

  @Test
  void switchesTheMode() throws Exception {
    when(botModeService.botMode()).thenReturn(BotMode.TRAINING);

    mockMvc
        .perform(
            put(BOT_MODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"TRAINING\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("TRAINING"));
    verify(tradingBotService).switchBotMode(BotMode.TRAINING);
  }

  @Test
  void unknownModeIsABadRequest() throws Exception {
    mockMvc
        .perform(
            put(BOT_MODE).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"PAPER\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingModeIsABadRequest() throws Exception {
    doThrow(new IllegalArgumentException("The bot mode must be TRADING or TRAINING"))
        .when(tradingBotService)
        .switchBotMode(null);

    mockMvc
        .perform(put(BOT_MODE).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void switchingWhileALiveBotRunsIsAConflict() throws Exception {
    doThrow(new LiveBotRunningException("binance"))
        .when(tradingBotService)
        .switchBotMode(BotMode.TRAINING);

    mockMvc
        .perform(
            put(BOT_MODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"TRAINING\"}"))
        .andExpect(status().isConflict());
  }
}
