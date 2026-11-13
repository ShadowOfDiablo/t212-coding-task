package com.tihomir.t212.tradingbot.algorithm;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Every {@link TradingAlgorithm} bean, by type. Adding an algorithm needs no change here. */
@Service
public final class AlgorithmRegistryService {

  private final Map<AlgorithmType, TradingAlgorithm> algorithmsByType;
  private final TradingAlgorithmList algorithms;

  public AlgorithmRegistryService(List<TradingAlgorithm> algorithms) {
    algorithms.forEach(AlgorithmRegistryService::checkOptionNames);
    this.algorithmsByType =
        algorithms.stream()
            .collect(
                Collectors.toMap(
                    TradingAlgorithm::type,
                    Function.identity(),
                    AlgorithmRegistryService::refuseDuplicateType));
    this.algorithms = new TradingAlgorithmList(algorithms);
  }

  /**
   * @throws UnknownAlgorithmException if no algorithm has this type
   */
  public TradingAlgorithm get(AlgorithmType algorithmType) {
    TradingAlgorithm algorithm = algorithmsByType.get(algorithmType);
    if (algorithm == null) {
      throw new UnknownAlgorithmException(algorithmType);
    }
    return algorithm;
  }

  public TradingAlgorithmList all() {
    return algorithms;
  }

  private static void checkOptionNames(TradingAlgorithm algorithm) {
    if (!Collections.disjoint(
        algorithm.optionDefinitions().names(), TradingAlgorithm.SHARED_DEFINITIONS.names())) {
      throw new IllegalStateException(
          algorithm.name() + " reuses the name of a shared setting for one of its own");
    }
  }

  private static TradingAlgorithm refuseDuplicateType(
      TradingAlgorithm first, TradingAlgorithm next) {
    throw new IllegalStateException(
        "Two algorithms share the type "
            + first.type()
            + ": "
            + first.name()
            + " and "
            + next.name());
  }
}
