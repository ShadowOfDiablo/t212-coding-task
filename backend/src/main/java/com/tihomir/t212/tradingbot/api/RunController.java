package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.RunningTraining;
import com.tihomir.t212.tradingbot.bot.TrainingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/{broker}")
public class RunController {

  private static final String DEFAULT_WORST_POSITIONS = "10";
  private static final long MAX_WORST_POSITIONS = 100;

  private final TrainingService trainingService;
  private final AlgorithmRunService runService;

  public RunController(TrainingService trainingService, AlgorithmRunService runService) {
    this.trainingService = trainingService;
    this.runService = runService;
  }

  /** Starts a training in the background and answers with its run id at once. */
  @PostMapping("/training/runs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public TrainingRunStartedResponse startTraining(
      @PathVariable String broker, @Valid @RequestBody TrainingRunRequestBody body) {
    return new TrainingRunStartedResponse(
        trainingService.startTraining(body.toTrainingRunRequest(broker)));
  }

  @GetMapping("/training")
  public TrainingStatusResponse getTrainingStatus() {
    boolean editable = trainingService.areParametersEditable();
    Optional<Long> runningRunId =
        trainingService
            .latestTraining()
            .filter(training -> !training.isFinished())
            .map(RunningTraining::runId);
    return new TrainingStatusResponse(!editable, editable, runningRunId);
  }

  @GetMapping("/runs")
  public RecordedRunListResponse getRuns(@PathVariable String broker) {
    return RecordedRunListResponse.of(runService.getRuns(broker));
  }

  @GetMapping("/runs/{runId}")
  public RunDetailsResponse getRun(@PathVariable String broker, @PathVariable long runId) {
    return RunDetailsResponse.of(runService.getRun(broker, runId));
  }

  @GetMapping("/runs/{runId}/trades")
  public TradeListResponse getTrades(@PathVariable String broker, @PathVariable long runId) {
    return TradeListResponse.of(runService.getTrades(broker, runId));
  }

  @GetMapping("/runs/{runId}/positions/worst")
  public PositionListResponse getWorstPositions(
      @PathVariable String broker,
      @PathVariable long runId,
      @RequestParam(defaultValue = DEFAULT_WORST_POSITIONS) @Positive @Max(MAX_WORST_POSITIONS)
          int limit) {
    return PositionListResponse.of(runService.getWorstPositions(broker, runId, limit));
  }
}
