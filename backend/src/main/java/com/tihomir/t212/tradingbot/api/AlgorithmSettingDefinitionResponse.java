package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingDefinition;
import java.math.BigDecimal;
import java.util.Optional;

/** {@code defaultValue} is empty (JSON {@code null}) for an optional setting. */
public record AlgorithmSettingDefinitionResponse(
    String name,
    String label,
    boolean required,
    boolean wholeNumber,
    Optional<BigDecimal> defaultValue,
    BigDecimal minimum,
    BigDecimal maximum) {

  static AlgorithmSettingDefinitionResponse of(AlgorithmSettingDefinition definition) {
    return new AlgorithmSettingDefinitionResponse(
        definition.name(),
        definition.label(),
        definition.required(),
        definition.wholeNumber(),
        definition.defaultValue(),
        definition.minimum(),
        definition.maximum());
  }
}
