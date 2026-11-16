package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithmService;
import com.tihomir.t212.tradingbot.api.request.ReplaceSavedSettingsRequestBody;
import com.tihomir.t212.tradingbot.api.request.SaveAlgorithmRequestBody;
import com.tihomir.t212.tradingbot.api.response.AlgorithmListResponse;
import com.tihomir.t212.tradingbot.api.response.SavedAlgorithmListResponse;
import com.tihomir.t212.tradingbot.api.response.SavedAlgorithmResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/algorithms")
public class AlgorithmController {

  private final AlgorithmRegistryService algorithms;
  private final SavedAlgorithmService savedAlgorithms;

  public AlgorithmController(
      AlgorithmRegistryService algorithms, SavedAlgorithmService savedAlgorithms) {
    this.algorithms = algorithms;
    this.savedAlgorithms = savedAlgorithms;
  }

  /** Every algorithm with the definitions of its settings, for building the settings form. */
  @GetMapping
  public AlgorithmListResponse getAlgorithms() {
    return AlgorithmListResponse.of(algorithms.all());
  }

  @GetMapping("/saved")
  public SavedAlgorithmListResponse getSavedAlgorithms() {
    return SavedAlgorithmListResponse.of(savedAlgorithms.getSavedAlgorithms());
  }

  @PostMapping("/saved")
  @ResponseStatus(HttpStatus.CREATED)
  public SavedAlgorithmResponse saveAlgorithm(@Valid @RequestBody SaveAlgorithmRequestBody body) {
    return SavedAlgorithmResponse.of(
        savedAlgorithms.saveAlgorithm(body.name(), body.settings().toSettingsRequest()));
  }

  /** New settings under an existing entry; its name and place in the list stay. */
  @PutMapping("/saved/{savedAlgorithmId}")
  public SavedAlgorithmResponse replaceSavedSettings(
      @PathVariable long savedAlgorithmId,
      @Valid @RequestBody ReplaceSavedSettingsRequestBody body) {
    return SavedAlgorithmResponse.of(
        savedAlgorithms.replaceSettings(savedAlgorithmId, body.settings().toSettingsRequest()));
  }

  @DeleteMapping("/saved/{savedAlgorithmId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSavedAlgorithm(@PathVariable long savedAlgorithmId) {
    savedAlgorithms.deleteSavedAlgorithm(savedAlgorithmId);
  }
}
