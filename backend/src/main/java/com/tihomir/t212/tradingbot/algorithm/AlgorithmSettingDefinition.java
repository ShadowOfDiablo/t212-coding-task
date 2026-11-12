package com.tihomir.t212.tradingbot.algorithm;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * One numeric setting, described so a form can be built from it. A required setting always has a
 * default. An optional setting has none: left empty, it is skipped by every check.
 */
public record AlgorithmSettingDefinition(
    String name,
    String label,
    boolean required,
    boolean wholeNumber,
    Optional<BigDecimal> defaultValue,
    BigDecimal minimum,
    BigDecimal maximum) {

  public AlgorithmSettingDefinition {
    if (minimum.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(name + ": minimum is above maximum");
    }
    if (required != defaultValue.isPresent()) {
      throw new IllegalArgumentException(name + ": only a required setting has a default");
    }
    defaultValue
        .filter(value -> !accepts(value, wholeNumber, minimum, maximum))
        .ifPresent(value -> refuseDefault(name));
  }

  public static AlgorithmSettingDefinition requiredWholeNumber(
      String name, String label, int defaultValue, int minimum, int maximum) {
    return new AlgorithmSettingDefinition(
        name,
        label,
        true,
        true,
        Optional.of(BigDecimal.valueOf(defaultValue)),
        BigDecimal.valueOf(minimum),
        BigDecimal.valueOf(maximum));
  }

  public static AlgorithmSettingDefinition optionalWholeNumber(
      String name, String label, int minimum, int maximum) {
    return new AlgorithmSettingDefinition(
        name,
        label,
        false,
        true,
        Optional.empty(),
        BigDecimal.valueOf(minimum),
        BigDecimal.valueOf(maximum));
  }

  public static AlgorithmSettingDefinition requiredNumber(
      String name, String label, String defaultValue, String minimum, String maximum) {
    return new AlgorithmSettingDefinition(
        name,
        label,
        true,
        false,
        Optional.of(new BigDecimal(defaultValue)),
        new BigDecimal(minimum),
        new BigDecimal(maximum));
  }

  public static AlgorithmSettingDefinition optionalNumber(
      String name, String label, String minimum, String maximum) {
    return new AlgorithmSettingDefinition(
        name,
        label,
        false,
        false,
        Optional.empty(),
        new BigDecimal(minimum),
        new BigDecimal(maximum));
  }

  boolean accepts(BigDecimal value) {
    return accepts(value, wholeNumber, minimum, maximum);
  }

  private static boolean accepts(
      BigDecimal value, boolean wholeNumber, BigDecimal minimum, BigDecimal maximum) {
    boolean inRange = value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    return inRange && (!wholeNumber || value.stripTrailingZeros().scale() <= 0);
  }

  private static void refuseDefault(String name) {
    throw new IllegalArgumentException(name + ": default value is not allowed");
  }
}
