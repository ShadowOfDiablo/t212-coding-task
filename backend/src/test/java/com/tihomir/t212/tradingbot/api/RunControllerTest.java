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
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithm;
import com.tihomir.t212.tradingbot.bot.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.NoSuccessfulTuningTrialException;
import com.tihomir.t212.tradingbot.bot.PortfolioPoint;
import com.tihomir.t212.tradingbot.bot.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.RecordedRun;
import com.tihomir.t212.tradingbot.bot.RunCounts;
import com.tihomir.t212.tradingbot.bot.RunDetails;
import com.tihomir.t212.tradingbot.bot.RunNotFoundException;
import com.tihomir.t212.tradingbot.bot.RunStatus;
import com.tihomir.t212.tradingbot.bot.SettingsSource;
import com.tihomir.t212.tradingbot.bot.TrainingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.TrainingRunRequest;
import com.tihomir.t212.tradingbot.bot.TrainingService;
import com.tihomir.t212.tradingbot.bot.TunedSettingRange;
import com.tihomir.t212.tradingbot.bot.TuningRequest;
import com.tihomir.t212.tradingbot.bot.TuningResult;
import com.tihomir.t212.tradingbot.bot.TuningService;
import com.tihomir.t212.tradingbot.bot.UnrecordedRun;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.StoredPosition;
import com.tihomir.t212.tradingbot.position.StoredPositionList;
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
  private static final String RUNS = "/api/brokers/binance/runs";
  private static final Instant FROM = Instant.parse("2020-01-01T00:00:00Z");
  private static final String RUN_BODY =
      """
      {"candles": {"ticker": "BTCUSDT", "from": "2020-01-01T00:00:00Z",
                   "to": "2020-02-01T00:00:00Z"},
       "settings": {"algorithmType": "SIMPLE_MOVING_AVERAGE", "timeframe": "5m",
                    "liveOnTick": true, "timePeriod": 20,
                    "stopLossPercent": 2, "takeProfitPercent": 3},
       "startingBalance": 1000}
      """;

  private static final String TUNINGS = "/api/brokers/binance/training/tunings";
  private static final String TUNING_BODY =
      """
      {"candles": {"ticker": "BTCUSDT", "from": "2020-01-01T00:00:00Z",
                   "to": "2020-02-01T00:00:00Z"},
       "checkFrom": "2020-01-20T00:00:00Z",
       "settings": {"algorithmType": "SIMPLE_MOVING_AVERAGE", "timeframe": "5m",
                    "liveOnTick": true},
       "startingBalance": 1000,
       "settingRanges": [{"name": "timePeriod", "minimum": 5, "maximum": 300}],
       "timeframes": ["5m", "1h"],
       "maxTrials": 50}
      """;

  @Autowired MockMvc mockMvc;

  @MockitoBean TrainingService trainingService;
  @MockitoBean TuningService tuningService;
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
        .andExpect(jsonPath("$.run.algorithmId").value(1))
        .andExpect(jsonPath("$.settings.algorithmType").value("SIMPLE_MOVING_AVERAGE"))
        .andExpect(jsonPath("$.settings.timePeriod").value(20))
        .andExpect(jsonPath("$.settings.takeProfitPercent").isEmpty())
        .andExpect(jsonPath("$.points[0].value").value(1000));

    ArgumentCaptor<TrainingRunRequest> request = ArgumentCaptor.forClass(TrainingRunRequest.class);
    verify(trainingService).runTraining(request.capture());
    assertThat(request.getValue().settings().values())
        .containsEntry(TradingAlgorithm.TIME_PERIOD, BigDecimal.valueOf(20))
        .containsEntry(TradingAlgorithm.STOP_LOSS_PERCENT, BigDecimal.valueOf(2))
        .containsEntry(TradingAlgorithm.TAKE_PROFIT_PERCENT, BigDecimal.valueOf(3))
        .doesNotContainKey(TradingAlgorithm.ORDER_SIZE_PERCENT);
  }

  @Test
  void checksTheNestedBodiesWithValid() throws Exception {
    String endsBeforeItStarts = RUN_BODY.replace("2020-02-01", "2019-02-01");
    String takeProfitTooHigh =
        RUN_BODY.replace("\"takeProfitPercent\": 3", "\"takeProfitPercent\": 101");
    String timerWithoutTime = RUN_BODY.replace("\"liveOnTick\": true", "\"liveOnTick\": false");
    String blankTicker = RUN_BODY.replace("\"ticker\": \"BTCUSDT\"", "\"ticker\": \"\"");

    expectBadRequest(endsBeforeItStarts, "from must be before to");
    expectBadRequest(
        takeProfitTooHigh, "settings.takeProfitPercent must be less than or equal to 100");
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
  void tunesAndAnswersWithTheRecordedRunAndTheTuneReturn() throws Exception {
    when(tuningService.runTuning(any(TuningRequest.class)))
        .thenReturn(
            new TuningResult(
                7,
                new BigDecimal("9.8"),
                50,
                Optional.of(
                    new UnrecordedRun(
                        new BigDecimal("1005"), RunCounts.NONE, PortfolioPointList.NONE))));
    when(runService.getRun("binance", 7)).thenReturn(details());

    mockMvc
        .perform(post(TUNINGS).contentType(MediaType.APPLICATION_JSON).content(TUNING_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.run.run.id").value(7))
        .andExpect(jsonPath("$.run.run.settingsSource").value("MANUAL"))
        .andExpect(jsonPath("$.bestTuneReturnPercent").value(9.8))
        .andExpect(jsonPath("$.baseRun.finalValue").value(1005))
        .andExpect(jsonPath("$.trialCount").value(50));

    ArgumentCaptor<TuningRequest> request = ArgumentCaptor.forClass(TuningRequest.class);
    verify(tuningService).runTuning(request.capture());
    assertThat(request.getValue().checkFrom()).isEqualTo(Instant.parse("2020-01-20T00:00:00Z"));
    assertThat(request.getValue().settingRanges())
        .containsExactly(
            new TunedSettingRange(
                TradingAlgorithm.TIME_PERIOD, BigDecimal.valueOf(5), BigDecimal.valueOf(300)));
    assertThat(request.getValue().timeframes())
        .containsExactly(CandleInterval.FIVE_MINUTES, CandleInterval.ONE_HOUR);
  }

  @Test
  void checksTheTuningBodyAndAnswers422WhenNoTrySucceeded() throws Exception {
    String checkAfterTheEnd = TUNING_BODY.replace("2020-01-20", "2020-03-01");
    String tooManyTrials = TUNING_BODY.replace("\"maxTrials\": 50", "\"maxTrials\": 201");
    String rangeUpsideDown = TUNING_BODY.replace("\"maximum\": 300", "\"maximum\": 1");
    expectBadTuningRequest(
        checkAfterTheEnd, "checkFrom must lie after candles.from and before candles.to");
    expectBadTuningRequest(tooManyTrials, "maxTrials must be less than or equal to 200");
    expectBadTuningRequest(rangeUpsideDown, "a tuned setting's minimum is at most its maximum");
    verifyNoInteractions(tuningService);

    when(tuningService.runTuning(any(TuningRequest.class)))
        .thenThrow(new NoSuccessfulTuningTrialException(50));
    mockMvc
        .perform(post(TUNINGS).contentType(MediaType.APPLICATION_JSON).content(TUNING_BODY))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void tellsWhetherTheSettingsCanBeEdited() throws Exception {
    when(trainingService.isTrainingRunning()).thenReturn(true);
    when(tuningService.progressPercent()).thenReturn(Optional.of(40));

    mockMvc
        .perform(get("/api/brokers/binance/training"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(true))
        .andExpect(jsonPath("$.tuningProgressPercent").value(40));
  }

  @Test
  void answersWithTheWorstPositionsOfARun() throws Exception {
    when(runService.getWorstPositions("binance", 7, 3))
        .thenReturn(new StoredPositionList(List.of(new StoredPosition(11, closedPosition()))));

    mockMvc
        .perform(get(RUNS + "/7/positions/worst").param("limit", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positions[0].id").value(11))
        .andExpect(jsonPath("$.positions[0].durationSeconds").value(3600))
        .andExpect(jsonPath("$.positions[0].realisedPnlPercent").value(-5))
        .andExpect(jsonPath("$.positions[0].mae").value(-8));
  }

  @Test
  void refusesALimitOutOfRangeAndAnswersNotFoundForAnUnknownRun() throws Exception {
    when(runService.getWorstPositions("binance", 8, 3)).thenThrow(new RunNotFoundException(8));

    mockMvc
        .perform(get(RUNS + "/7/positions/worst").param("limit", "0"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(RUNS + "/7/positions/worst").param("limit", "101"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(RUNS + "/8/positions/worst").param("limit", "3"))
        .andExpect(status().isNotFound());
  }

  private static Position closedPosition() {
    return new Position(
        7,
        "binance",
        "BTCUSDT",
        PositionStatus.CLOSED,
        FROM,
        Optional.of(FROM.plusSeconds(3600)),
        new BigDecimal("100"),
        new BigDecimal("-5"),
        new BigDecimal("-8"),
        new BigDecimal("2"));
  }

  private void expectBadRequest(String body, String message) throws Exception {
    mockMvc
        .perform(post(TRAINING_RUNS).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(containsString(message)));
  }

  private void expectBadTuningRequest(String body, String message) throws Exception {
    mockMvc
        .perform(post(TUNINGS).contentType(MediaType.APPLICATION_JSON).content(body))
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
            1,
            SettingsSource.MANUAL,
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
            AlgorithmType.SIMPLE_MOVING_AVERAGE,
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
        settings,
        new PortfolioPointList(
            List.of(new PortfolioPoint(FROM, new BigDecimal("1000"), new BigDecimal("1000")))));
  }
}
