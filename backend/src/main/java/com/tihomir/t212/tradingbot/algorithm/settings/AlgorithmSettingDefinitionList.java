package com.tihomir.t212.tradingbot.algorithm.settings;

import com.tihomir.t212.tradingbot.algorithm.exception.InvalidAlgorithmSettingsException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record AlgorithmSettingDefinitionList(List<AlgorithmSettingDefinition> definitions) {

  public static final AlgorithmSettingDefinitionList NONE =
      new AlgorithmSettingDefinitionList(List.of());

  public AlgorithmSettingDefinitionList {
    definitions = List.copyOf(definitions);
  }

  public Set<String> names() {
    return definitions.stream().map(AlgorithmSettingDefinition::name).collect(Collectors.toSet());
  }

  public AlgorithmSettingDefinitionList followedBy(AlgorithmSettingDefinitionList more) {
    return new AlgorithmSettingDefinitionList(
        Stream.concat(definitions.stream(), more.definitions().stream()).toList());
  }

  /**
   * Checks requested values against these definitions. A required setting without a value takes its
   * default; an optional one stays empty and none of its checks run.
   *
   * @param requestedValues values by name; a value may be {@code null}
   * @throws InvalidAlgorithmSettingsException for an unknown name or a value that isn't allowed
   */
  public AlgorithmSettingValues resolveValues(Map<String, BigDecimal> requestedValues) {
    Set<String> names = names();
    requestedValues.keySet().stream()
        .filter(name -> !names.contains(name))
        .findFirst()
        .ifPresent(AlgorithmSettingDefinitionList::refuseUnknownSetting);
    return new AlgorithmSettingValues(
        definitions.stream()
            .flatMap(definition -> resolvedEntry(definition, requestedValues).stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private static Optional<Map.Entry<String, BigDecimal>> resolvedEntry(
      AlgorithmSettingDefinition definition, Map<String, BigDecimal> requestedValues) {
    return resolvedValue(definition, requestedValues.get(definition.name()))
        .map(value -> Map.entry(definition.name(), value));
  }

  private static Optional<BigDecimal> resolvedValue(
      AlgorithmSettingDefinition definition, BigDecimal requestedValue) {
    if (requestedValue == null) {
      return definition.defaultValue();
    }
    if (!definition.accepts(requestedValue)) {
      throw new InvalidAlgorithmSettingsException(rangeMessage(definition));
    }
    return Optional.of(AlgorithmSettingValues.plainValue(requestedValue));
  }

  private static String rangeMessage(AlgorithmSettingDefinition definition) {
    String range =
        definition.name()
            + " must be between "
            + definition.minimum().toPlainString()
            + " and "
            + definition.maximum().toPlainString();
    if (definition.wholeNumber()) {
      return range + ", as a whole number";
    }
    return range;
  }

  private static void refuseUnknownSetting(String name) {
    throw new InvalidAlgorithmSettingsException("Unknown setting: " + name);
  }
}
