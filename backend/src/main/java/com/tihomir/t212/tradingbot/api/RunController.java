package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.TrainingService;
import com.tihomir.t212.tradingbot.bot.TuningResult;
import com.tihomir.t212.tradingbot.bot.TuningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
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
  private final TuningService tuningService;
  private final AlgorithmRunService runService;

  public RunController(
      TrainingService trainingService,
      TuningService tuningService,
      AlgorithmRunService runService) {
    this.trainingService = trainingService;
    this.tuningService = tuningService;
    this.runService = runService;
  }

  /** Runs a training to the end and answers with the recorded run. */
  @PostMapping("/training/runs")
  @ResponseStatus(HttpStatus.CREATED)
  public RunDetailsResponse runTraining(
      @PathVariable String broker, @Valid @RequestBody TrainingRunRequestBody body) {
    long runId = trainingService.runTraining(body.toTrainingRunRequest(broker));
    return RunDetailsResponse.of(runService.getRun(broker, runId));
  }

  /** Tunes settings automatically and answers with the best one, recorded as a training run. */
  @PostMapping("/training/tunings")
  @ResponseStatus(HttpStatus.CREATED)
  public TuningResponse runTuning(
      @PathVariable String broker, @Valid @RequestBody TuningRequestBody body) {
    TuningResult result = tuningService.runTuning(body.toTuningRequest(broker));
    return new TuningResponse(
        RunDetailsResponse.of(runService.getRun(broker, result.runId())),
        result.bestTuneReturnPercent(),
        result.trialCount(),
        result.baseRun().map(UnrecordedRunResponse::of));
  }

  @GetMapping("/training")
  public TrainingStatusResponse getTrainingStatus() {
    return new TrainingStatusResponse(
        trainingService.isTrainingRunning(), tuningService.progressPercent());
  }

  @GetMapping("/runs")
  public RecordedRunListResponse getRuns(@PathVariable String broker) {
    return RecordedRunListResponse.of(runService.getRuns(broker));
  }

  @GetMapping("/runs/{runId}")
  public RunDetailsResponse getRun(@PathVariable String broker, @PathVariable long runId) {
    return RunDetailsResponse.of(runService.getRun(broker, runId));
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
