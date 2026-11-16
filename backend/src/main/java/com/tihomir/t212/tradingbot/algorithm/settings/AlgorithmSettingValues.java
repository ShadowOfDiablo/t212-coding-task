package com.tihomir.t212.tradingbot.algorithm.settings;

import com.tihomir.t212.tradingbot.algorithm.exception.InvalidAlgorithmSettingsException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Setting values by name. An optional setting that was left empty is simply absent. */
public record AlgorithmSettingValues(Map<String, BigDecimal> values) {

  public static final AlgorithmSettingValues NONE = new AlgorithmSettingValues(Map.of());

  public AlgorithmSettingValues {
    values = Map.copyOf(values);
  }

  public Optional<BigDecimal> optionalValue(String name) {
    return Optional.ofNullable(values.get(name));
  }

  public Optional<Integer> optionalWholeNumber(String name) {
    return optionalValue(name).map(BigDecimal::intValueExact);
  }

  /** A required value; resolved values always hold every required setting. */
  public BigDecimal value(String name) {
    return optionalValue(name)
        .orElseThrow(() -> new InvalidAlgorithmSettingsException("Missing setting: " + name));
  }

  public int wholeNumber(String name) {
    return value(name).intValueExact();
  }

  /** The value without trailing zeros, in plain form: 100.00 becomes 100, not 1E+2. */
  public static BigDecimal plainValue(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    if (stripped.scale() < 0) {
      return stripped.setScale(0);
    }
    return stripped;
  }

  public AlgorithmSettingValues only(Set<String> names) {
    return new AlgorithmSettingValues(
        values.entrySet().stream()
            .filter(entry -> names.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }
}
