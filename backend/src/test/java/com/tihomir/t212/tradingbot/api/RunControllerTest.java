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
import com.tihomir.t212.tradingbot.bot.PortfolioPoint;
import com.tihomir.t212.tradingbot.bot.PortfolioPointList;
import com.tihomir.t212.tradingbot.bot.RecordedRun;
import com.tihomir.t212.tradingbot.bot.RunCounts;
import com.tihomir.t212.tradingbot.bot.RunDetails;
import com.tihomir.t212.tradingbot.bot.RunNotFoundException;
import com.tihomir.t212.tradingbot.bot.RunStatus;
import com.tihomir.t212.tradingbot.bot.RunningTraining;
import com.tihomir.t212.tradingbot.bot.TrainingModeRequiredException;
import com.tihomir.t212.tradingbot.bot.TrainingRunRequest;
import com.tihomir.t212.tradingbot.bot.TrainingService;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.mode.BotMode;
import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.StoredPosition;
import com.tihomir.t212.tradingbot.position.StoredPositionList;
import com.tihomir.t212.tradingbot.position.Trade;
import com.tihomir.t212.tradingbot.position.TradeList;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
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
                    "riskPerTradePercent": 3, "stopLossPercent": 2},
       "startingBalance": 1000}
      """;

  @Autowired MockMvc mockMvc;

  @MockitoBean TrainingService trainingService;
  @MockitoBean AlgorithmRunService runService;

  @Test
  void startsATrainingAndAnswersWithItsRunId() throws Exception {
    when(trainingService.startTraining(any(TrainingRunRequest.class))).thenReturn(7L);

    mockMvc
        .perform(post(TRAINING_RUNS).contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.runId").value(7));

    ArgumentCaptor<TrainingRunRequest> request = ArgumentCaptor.forClass(TrainingRunRequest.class);
    verify(trainingService).startTraining(request.capture());
    assertThat(request.getValue().settings().values())
        .containsEntry(TradingAlgorithm.TIME_PERIOD, BigDecimal.valueOf(20))
        .containsEntry(TradingAlgorithm.STOP_LOSS_PERCENT, BigDecimal.valueOf(2))
        .doesNotContainKey(TradingAlgorithm.ORDER_SIZE_PERCENT);
  }

  @Test
  void answersWithTheRecordedRun() throws Exception {
    when(runService.getRun("binance", 7)).thenReturn(details());

    mockMvc
        .perform(get(RUNS + "/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.run.id").value(7))
        .andExpect(jsonPath("$.run.status").value("COMPLETED"))
        .andExpect(jsonPath("$.run.finishedAt").isEmpty())
        .andExpect(jsonPath("$.run.algorithmId").value(1))
        .andExpect(jsonPath("$.settings.algorithmType").value("SIMPLE_MOVING_AVERAGE"))
        .andExpect(jsonPath("$.settings.timePeriod").value(20))
        .andExpect(jsonPath("$.settings.riskPerTradePercent").isEmpty())
        .andExpect(jsonPath("$.points[0].value").value(1000));
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
    when(trainingService.startTraining(any(TrainingRunRequest.class)))
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
    when(trainingService.latestTraining())
        .thenReturn(Optional.of(new RunningTraining("binance", 7, BigDecimal.ONE)));

    mockMvc
        .perform(get("/api/brokers/binance/training"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(true))
        .andExpect(jsonPath("$.areParametersEditable").value(false))
        .andExpect(jsonPath("$.runId").value(7));
  }

  @Test
  void answersUnavailableWhenTheTrainingThreadIsShutDown() throws Exception {
    when(trainingService.startTraining(any(TrainingRunRequest.class)))
        .thenThrow(new RejectedExecutionException("shut down"));

    mockMvc
        .perform(post(TRAINING_RUNS).contentType(MediaType.APPLICATION_JSON).content(RUN_BODY))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void answersWithTheTradesAndTheWorstPositionsOfARun() throws Exception {
    when(runService.getTrades("binance", 7)).thenReturn(new TradeList(List.of(trade())));
    when(runService.getWorstPositions("binance", 7, 3))
        .thenReturn(new StoredPositionList(List.of(new StoredPosition(11, closedPosition()))));

    mockMvc
        .perform(get(RUNS + "/7/trades"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trades[0].realisedPnl").value(-5))
        .andExpect(jsonPath("$.trades[0].buyOrderId").value(31))
        .andExpect(jsonPath("$.trades[0].sellOrderId").value(32))
        .andExpect(jsonPath("$.trades[0].soldAt").value("2020-01-01T01:00:00Z"));
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
    when(runService.getTrades("binance", 8)).thenThrow(new RunNotFoundException(8));

    mockMvc
        .perform(get(RUNS + "/7/positions/worst").param("limit", "0"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(RUNS + "/7/positions/worst").param("limit", "101"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get(RUNS + "/8/trades")).andExpect(status().isNotFound());
  }

  private static Trade trade() {
    return new Trade(
        7,
        31,
        32,
        FROM,
        FROM.plusSeconds(3600),
        BigDecimal.ONE,
        new BigDecimal("100"),
        new BigDecimal("95"),
        BigDecimal.ZERO,
        new BigDecimal("-5"));
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

  private static RunDetails details() {
    RecordedRun run =
        new RecordedRun(
            7,
            BotMode.TRAINING,
            "binance",
            "BTCUSDT",
            1,
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
