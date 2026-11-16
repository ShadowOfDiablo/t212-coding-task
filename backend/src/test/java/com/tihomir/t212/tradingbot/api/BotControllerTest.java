package com.tihomir.t212.tradingbot.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.exception.LiveBotNotRunningException;
import com.tihomir.t212.tradingbot.bot.exception.TickerSettingsMissingException;
import com.tihomir.t212.tradingbot.bot.exception.TradingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.live.TradingBotService;
import com.tihomir.t212.tradingbot.bot.model.LiveBotStatus;
import com.tihomir.t212.tradingbot.bot.model.LiveBotStatusList;
import com.tihomir.t212.tradingbot.bot.run.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.run.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.run.RunCounts;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotController.class)
class BotControllerTest {

  private static final String BOTS = "/api/brokers/binance/bots";
  private static final String BTC_BOT = BOTS + "/BTCUSDT";
  private static final String SETTINGS_BODY =
      """
      {"algorithmType": "SIMPLE_MOVING_AVERAGE", "timeframe": "15m",
       "liveOnTick": false, "updateTimeSeconds": 30, "timePeriod": 50}
      """;

  @Autowired MockMvc mockMvc;

  @MockitoBean TradingBotService tradingBotService;
  @MockitoBean AlgorithmRunService runService;

  @Test
  void listsTheRunningBots() throws Exception {
    when(tradingBotService.getBotStatuses("binance"))
        .thenReturn(new LiveBotStatusList(List.of(runningBot(false))));

    mockMvc
        .perform(get(BOTS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bots[0].ticker").value("BTCUSDT"))
        .andExpect(jsonPath("$.bots[0].runId").value(4));
  }

  @Test
  void startsATickersBotAndAnswersWithItsStatus() throws Exception {
    when(tradingBotService.getBotStatus("binance", "BTCUSDT"))
        .thenReturn(Optional.of(runningBot(false)));

    mockMvc
        .perform(post(BTC_BOT + "/start"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.runId").value(4))
        .andExpect(jsonPath("$.currentParameters.timePeriod").value(50))
        .andExpect(jsonPath("$.buyCount").value(1));
    verify(tradingBotService).startBot("binance", "BTCUSDT");
  }

  @Test
  void answersConflictForATickerWithoutSettingsOrOutsideTradingMode() throws Exception {
    when(tradingBotService.startBot("binance", "ETHUSDT"))
        .thenThrow(new TickerSettingsMissingException("binance", "ETHUSDT"));
    when(tradingBotService.startAllBots("binance")).thenThrow(new TradingModeRequiredException());

    mockMvc.perform(post(BOTS + "/ETHUSDT/start")).andExpect(status().isConflict());
    mockMvc.perform(post(BOTS + "/start-all")).andExpect(status().isConflict());
  }

  @Test
  void stopsEveryBotAndAnswersWithWhatStillRuns() throws Exception {
    when(tradingBotService.getBotStatuses("binance")).thenReturn(new LiveBotStatusList(List.of()));

    mockMvc
        .perform(post(BOTS + "/stop-all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bots").isEmpty());
    verify(tradingBotService).stopAllBots("binance");
  }

  @Test
  void savesNewParametersAndAnswersConflictWhenTheTickersBotDoesNotRun() throws Exception {
    when(tradingBotService.updateCurrentParameters(
            eq("binance"), eq("BTCUSDT"), any(AlgorithmSettingsRequest.class)))
        .thenReturn(runningBot(false));
    when(tradingBotService.stopBot("binance", "BTCUSDT"))
        .thenThrow(new LiveBotNotRunningException("binance", "BTCUSDT"));

    mockMvc
        .perform(
            put(BTC_BOT + "/current-parameters")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.algorithmId").value(3));
    mockMvc.perform(post(BTC_BOT + "/stop")).andExpect(status().isConflict());
  }

  @Test
  void pausesATickersBotAndAnswersConflictWhenItDoesNotRun() throws Exception {
    when(tradingBotService.pauseBot("binance", "BTCUSDT")).thenReturn(runningBot(true));
    when(tradingBotService.resumeBot("binance", "BTCUSDT"))
        .thenThrow(new LiveBotNotRunningException("binance", "BTCUSDT"));

    mockMvc
        .perform(post(BTC_BOT + "/pause"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paused").value(true));
    mockMvc.perform(post(BTC_BOT + "/resume")).andExpect(status().isConflict());
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
