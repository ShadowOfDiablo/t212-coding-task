package com.tihomir.t212.tradingbot.bot;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The values one try uses, by dimension name. A dimension the try didn't use is absent. */
record TuningPoint(Map<String, Double> values) {

  TuningPoint {
    values = Map.copyOf(values);
  }

  Optional<Double> value(String name) {
    return Optional.ofNullable(values.get(name));
  }

  TuningPoint without(Set<String> names) {
    return new TuningPoint(
        values.entrySet().stream()
            .filter(entry -> !names.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }
}
