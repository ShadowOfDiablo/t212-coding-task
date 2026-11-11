package com.tihomir.t212.tradingbot.broker.binance.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Request parameters in the order they are sent and signed. */
public record QueryParameters(Map<String, String> parameters) {

  public QueryParameters {
    parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
  }

  public QueryParameters withParameter(String name, String value) {
    Map<String, String> extended = new LinkedHashMap<>(parameters);
    extended.put(name, value);
    return new QueryParameters(extended);
  }

  public String queryString() {
    return parameters.entrySet().stream()
        .map(parameter -> parameter.getKey() + "=" + encode(parameter.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
