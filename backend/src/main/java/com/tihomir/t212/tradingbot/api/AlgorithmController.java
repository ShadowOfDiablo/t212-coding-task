package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/algorithms")
public class AlgorithmController {

  private final AlgorithmRegistryService algorithms;

  public AlgorithmController(AlgorithmRegistryService algorithms) {
    this.algorithms = algorithms;
  }

  /** Every algorithm with the definitions of its settings, for building the settings form. */
  @GetMapping
  public AlgorithmListResponse getAlgorithms() {
    return AlgorithmListResponse.of(algorithms.all());
  }
}
