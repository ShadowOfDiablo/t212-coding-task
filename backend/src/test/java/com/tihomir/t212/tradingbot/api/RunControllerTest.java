package com.tihomir.t212.tradingbot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.bot.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.PortfolioPoint;
import com.tihomir.t212.tradingbot.bot.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.RecordedRun;
import com.tihomir.t212.tradingbot.bot.RunCounts;
import com.tihomir.t212.tradingbot.bot.RunDetails;
import com.tihomir.t212.tradingbot.bot.RunEvent;
import com.tihomir.t212.tradingbot.bot.RunNotFoundException;
import com.tihomir.t212.tradingbot.bot.RunSettingsHistory;
import com.tihomir.t212.tradingbot.bot.RunSettingsRecord;
import com.tihomir.t212.tradingbot.bot.RunStatus;
import com.tihomir.t212.tradingbot.bot.TrainingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.TrainingRunRequest;
import com.tihomir.t212.tradingbot.bot.TrainingService;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BotMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RunController.class)
class RunControllerTest {

  private static final String TRAINING_RUNS = "/api/brokers/binance/training/runs";
  private static final Instant FROM = Instant.parse("2020-01-01T00:00:00Z");
  private static final String RUN_BODY =
      """
      {"candles": {"ticker": "BTCUSDT", "from": "2020-01-01T00:00:00Z",
                   "to": "2020-02-01T00:00:00Z"},
       "settings": {"algorithmId": 1, "timeframe": "FIVE_MINUTES",
                    "liveOnTick": true, "timePeriod": 20,
                    "riskPerTradePercent": 3, "stopLossPercent": 2},
       "startingBalance": 1000}
      """;

  @Autowired MockMvc mockMvc;

  @MockitoBean TrainingService trainingService;
  @MockitoBean AlgorithmRunService runService;

  @Test
  void runsATrainingAndAnswersWithTheRecordedRun() throws Exception {
    when(trainingService.runTraining(any(TrainingRunRequest.class))).thenReturn(7L);
    when(runService.getRun("binance", 7)).thenReturn(details());

    mockMvc
        .perform(post(TRAINING_RUNS).contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.run.id").value(7))
        .andExpect(jsonPath("$.run.status").value("COMPLETED"))
        .andExpect(jsonPath("$.run.finishedAt").isEmpty())
        .andExpect(jsonPath("$.settingsHistory[0].event").value("STARTED"))
        .andExpect(jsonPath("$.settingsHistory[0].settings.timePeriod").value(20))
        .andExpect(jsonPath("$.settingsHistory[0].settings.riskPerTradePercent").isEmpty())
        .andExpect(jsonPath("$.points[0].value").value(1000));

    ArgumentCaptor<TrainingRunRequest> request = ArgumentCaptor.forClass(TrainingRunRequest.class);
    verify(trainingService).runTraining(request.capture());
    assertThat(request.getValue().settings().values())
        .containsEntry(TradingAlgorithm.TIME_PERIOD, BigDecimal.valueOf(20))
        .containsEntry(TradingAlgorithm.STOP_LOSS_PERCENT, BigDecimal.valueOf(2))
        .doesNotContainKey(TradingAlgorithm.ORDER_SIZE_PERCENT);
  }

  @Test
  void checksTheNestedBodiesWithValid() throws Exception {
    String endsBeforeItStarts = RUN_BODY.replace("2020-02-01", "2019-02-01");
    String riskWithoutStop = RUN_BODY.replace(", \"stopLossPercent\": 2", "");
    String timerWithoutTime = RUN_BODY.replace("\"liveOnTick\": true", "\"liveOnTick\": false");
    String blankTicker = RUN_BODY.replace("\"ticker\": \"BTCUSDT\"", "\"ticker\": \"\"");

    expectBadRequest(endsBeforeItStarts, "from must be before to");
    expectBadRequest(riskWithoutStop, "riskPerTradePercent and stopLossPercent are set together");
    expectBadRequest(timerWithoutTime, "updateTimeSeconds is set exactly when liveOnTick is off");
    expectBadRequest(blankTicker, "candles.ticker must not be blank");
    verifyNoInteractions(trainingService);
  }

  @Test
  void answersConflictOutsideTrainingModeAndNotFoundForAnUnknownRun() throws Exception {
    when(trainingService.runTraining(any(TrainingRunRequest.class)))
        .thenThrow(new TrainingModeRequiredException());
    when(runService.getRun("binance", 99)).thenThrow(new RunNotFoundException(99));

    mockMvc
        .perform(post(TRAINING_RUNS).contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
        .andExpect(status().isConflict());
    mockMvc.perform(get("/api/brokers/binance/runs/99")).andExpect(status().isNotFound());
  }

  @Test
  void tellsWhetherTheSettingsCanBeEdited() throws Exception {
    when(trainingService.areParametersEditable()).thenReturn(false);

    mockMvc
        .perform(get("/api/brokers/binance/training"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(true))
        .andExpect(jsonPath("$.areParametersEditable").value(false));
  }

  private void expectBadRequest(String body, String message) throws Exception {
    mockMvc
        .perform(post(TRAINING_RUNS).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(containsString(message)));
  }

  private static RunDetails details() {
    RecordedRun run =
        new RecordedRun(
            7,
            BotMode.TRAINING,
            "binance",
            "BTCUSDT",
            RunStatus.COMPLETED,
            FROM,
            Optional.empty(),
            Optional.of(FROM),
            Optional.of(FROM),
            new BigDecimal("1000"),
            Optional.of(new BigDecimal("1010")),
            RunCounts.NONE,
            Optional.empty());
    AlgorithmSettings settings =
        new AlgorithmSettings(
            SimpleMovingAverageAlgorithm.ID,
            20,
            CandleInterval.FIVE_MINUTES,
            true,
            Optional.empty(),
            new BigDecimal("100"),
            Optional.empty(),
            Optional.empty(),
            AlgorithmSettingValues.NONE);
    return new RunDetails(
        run,
        new RunSettingsHistory(List.of(new RunSettingsRecord(RunEvent.STARTED, FROM, 1, settings))),
        new PortfolioPointList(
            List.of(new PortfolioPoint(FROM, new BigDecimal("1000"), new BigDecimal("1000")))));
  }
}
