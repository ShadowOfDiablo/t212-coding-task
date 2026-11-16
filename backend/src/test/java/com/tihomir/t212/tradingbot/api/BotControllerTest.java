package com.tihomir.t212.tradingbot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.exception.LiveBotNotRunningException;
import com.tihomir.t212.tradingbot.bot.exception.TradingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.live.TradingBotService;
import com.tihomir.t212.tradingbot.bot.model.LiveBotStatus;
import com.tihomir.t212.tradingbot.bot.model.LiveRunRequest;
import com.tihomir.t212.tradingbot.bot.run.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.run.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.run.RunCounts;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotController.class)
class BotControllerTest {

  private static final String BOT = "/api/brokers/binance/bot";
  private static final String SETTINGS_BODY =
      """
      {"algorithmType": "SIMPLE_MOVING_AVERAGE", "timeframe": "15m",
       "liveOnTick": false, "updateTimeSeconds": 30, "timePeriod": 50}
      """;
  private static final String START_BODY =
      "{\"ticker\": \"BTCUSDT\", \"settings\": " + SETTINGS_BODY + "}";

  @Autowired MockMvc mockMvc;

  @MockitoBean TradingBotService tradingBotService;
  @MockitoBean AlgorithmRunService runService;

  @Test
  void showsABotThatDoesNotRun() throws Exception {
    when(tradingBotService.getBotStatus("binance")).thenReturn(Optional.empty());

    mockMvc.perform(get(BOT)).andExpect(status().isOk()).andExpect(jsonPath("$.runId").isEmpty());
  }

  @Test
  void startsTheBotAndAnswersWithItsStatus() throws Exception {
    when(tradingBotService.getBotStatus("binance")).thenReturn(Optional.of(runningBot()));

    mockMvc
        .perform(post(BOT + "/start").contentType(MediaType.APPLICATION_JSON).content(START_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.runId").value(4))
        .andExpect(jsonPath("$.currentParameters.timePeriod").value(50))
        .andExpect(jsonPath("$.buyCount").value(1));

    ArgumentCaptor<LiveRunRequest> request = ArgumentCaptor.forClass(LiveRunRequest.class);
    verify(tradingBotService).startBot(request.capture());
    assertThat(request.getValue().ticker()).isEqualTo("BTCUSDT");
    assertThat(request.getValue().settings().values())
        .containsEntry(TradingAlgorithm.UPDATE_TIME_SECONDS, BigDecimal.valueOf(30));
  }

  @Test
  void checksTheStartBodyAndAnswersConflictOutsideTradingMode() throws Exception {
    String timerWithoutTime = START_BODY.replace(", \"updateTimeSeconds\": 30", "");
    mockMvc
        .perform(
            post(BOT + "/start").contentType(MediaType.APPLICATION_JSON).content(timerWithoutTime))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value(containsString("updateTimeSeconds is set exactly when liveOnTick is off")));
    verifyNoInteractions(tradingBotService);

    when(tradingBotService.startBot(any(LiveRunRequest.class)))
        .thenThrow(new TradingModeRequiredException());
    mockMvc
        .perform(post(BOT + "/start").contentType(MediaType.APPLICATION_JSON).content(START_BODY))
        .andExpect(status().isConflict());
  }

  @Test
  void savesNewParametersAndAnswersConflictWhenNothingRuns() throws Exception {
    when(tradingBotService.updateCurrentParameters(
            eq("binance"), any(AlgorithmSettingsRequest.class)))
        .thenReturn(runningBot());
    when(tradingBotService.stopBot("binance")).thenThrow(new LiveBotNotRunningException("binance"));

    mockMvc
        .perform(
            put(BOT + "/current-parameters")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.algorithmId").value(3));
    mockMvc.perform(post(BOT + "/stop")).andExpect(status().isConflict());
  }

  @Test
  void pausesTheBotAndAnswersConflictWhenNothingRuns() throws Exception {
    when(tradingBotService.pauseBot("binance")).thenReturn(runningBot(true));
    when(tradingBotService.resumeBot("binance"))
        .thenThrow(new LiveBotNotRunningException("binance"));

    mockMvc
        .perform(post(BOT + "/pause"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paused").value(true));
    mockMvc.perform(post(BOT + "/resume")).andExpect(status().isConflict());
  }

  private static LiveBotStatus runningBot() {
    return runningBot(false);
  }

  private static LiveBotStatus runningBot(boolean paused) {
    return new LiveBotStatus(
        4,
        "BTCUSDT",
        paused,
        new AlgorithmSettings(
            AlgorithmType.SIMPLE_MOVING_AVERAGE,
            50,
            CandleInterval.FIFTEEN_MINUTES,
            false,
            Optional.of(30),
            new BigDecimal("100"),
            Optional.empty(),
            Optional.empty(),
            AlgorithmSettingValues.NONE),
        3,
        new RunCounts(1, 0, 0, 0, 0),
        PortfolioPointList.NONE);
  }
}
