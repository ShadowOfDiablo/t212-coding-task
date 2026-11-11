package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.RequestSigner;
import java.time.Clock;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
final class BinanceUserDataSubscription {

  static final String SUBSCRIBE_METHOD = "userDataStream.subscribe.signature";
  private static final String REQUEST_ID_PREFIX = "subscribe-";

  private final BinanceTradingProperties tradingProperties;
  private final RequestSigner signer;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  BinanceUserDataSubscription(
      BinanceTradingProperties tradingProperties,
      RequestSigner signer,
      JsonMapper jsonMapper,
      Clock clock) {
    this.tradingProperties = tradingProperties;
    this.signer = signer;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  boolean credentialsConfigured() {
    return tradingProperties.credentialsConfigured();
  }

  String subscribeRequest() {
    long timestamp = clock.millis();
    long recvWindowMillis = tradingProperties.recvWindow().toMillis();
    Map<String, String> signedParameters = new TreeMap<>();
    signedParameters.put("apiKey", tradingProperties.apiKey());
    signedParameters.put("recvWindow", String.valueOf(recvWindowMillis));
    signedParameters.put("timestamp", String.valueOf(timestamp));

    ObjectNode request = jsonMapper.createObjectNode();
    request.put("id", REQUEST_ID_PREFIX + timestamp);
    request.put("method", SUBSCRIBE_METHOD);
    ObjectNode params = request.putObject("params");
    params.put("apiKey", tradingProperties.apiKey());
    params.put("recvWindow", recvWindowMillis);
    params.put("timestamp", timestamp);
    params.put("signature", signer.sign(signaturePayload(signedParameters)));
    return jsonMapper.writeValueAsString(request);
  }

  private static String signaturePayload(Map<String, String> sortedParameters) {
    return sortedParameters.entrySet().stream()
        .map(parameter -> parameter.getKey() + "=" + parameter.getValue())
        .collect(Collectors.joining("&"));
  }
}
