package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.AlgorithmRunService;
import com.tihomir.t212.tradingbot.bot.TrainingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/{broker}")
public class RunController {

  private final TrainingService trainingService;
  private final AlgorithmRunService runService;

  public RunController(TrainingService trainingService, AlgorithmRunService runService) {
    this.trainingService = trainingService;
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

  @GetMapping("/training")
  public TrainingStatusResponse getTrainingStatus() {
    boolean editable = trainingService.areParametersEditable();
    return new TrainingStatusResponse(!editable, editable);
  }

  @GetMapping("/runs")
  public RecordedRunListResponse getRuns(@PathVariable String broker) {
    return RecordedRunListResponse.of(runService.getRuns(broker));
  }

  @GetMapping("/runs/{runId}")
  public RunDetailsResponse getRun(@PathVariable String broker, @PathVariable long runId) {
    return RunDetailsResponse.of(runService.getRun(broker, runId));
  }
}
