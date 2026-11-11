package com.tihomir.t212.tradingbot.broker.binance;

import com.tihomir.t212.tradingbot.broker.binance.util.OrderUtil;
import com.tihomir.t212.tradingbot.order.AccountBalanceSynchronizer;
import com.tihomir.t212.tradingbot.userdata.UserDataStreamConnection;
import com.tihomir.t212.tradingbot.userdata.UserDataStreamConnectionFactory;
import com.tihomir.t212.tradingbot.userdata.UserDataStreamHandler;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
final class BinanceUserDataStream implements UserDataStreamHandler, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(BinanceUserDataStream.class);
  private static final int SUCCESS_STATUS = 200;
  private static final String ACCOUNT_POSITION_EVENT = "outboundAccountPosition";
  private static final String EXECUTION_REPORT_EVENT = "executionReport";
  private static final String STREAM_TERMINATED_EVENT = "eventStreamTerminated";
  private static final String SERVER_SHUTDOWN_EVENT = "serverShutdown";

  private final BinanceUserDataStreamProperties streamProperties;
  private final BinanceUserDataSubscription subscription;
  private final UserDataStreamConnectionFactory connectionFactory;
  private final JsonMapper jsonMapper;
  private final AccountBalanceSynchronizer synchronizer;
  private Optional<UserDataStreamConnection> connection = Optional.empty();

  BinanceUserDataStream(
      BinanceUserDataStreamProperties streamProperties,
      BinanceUserDataSubscription subscription,
      UserDataStreamConnectionFactory connectionFactory,
      JsonMapper jsonMapper,
      AccountBalanceSynchronizer synchronizer) {
    this.streamProperties = streamProperties;
    this.subscription = subscription;
    this.connectionFactory = connectionFactory;
    this.jsonMapper = jsonMapper;
    this.synchronizer = synchronizer;
  }

  synchronized void start() {
    if (connection.isPresent()) {
      return;
    }
    if (!subscription.credentialsConfigured()) {
      log.info("Binance: no demo API key configured, the user data stream is off");
      return;
    }
    synchronizer.start();
    connection = Optional.of(connectionFactory.open(streamProperties.websocketApiUrl(), this));
  }

  @Override
  public void onConnected(UserDataStreamConnection openedConnection) {
    sendSubscribeRequest(openedConnection);
  }

  @Override
  public void onMessage(String message) {
    try {
      JsonNode json = jsonMapper.readTree(message);
      if (json.has("event")) {
        handleEvent(json.get("event"));
        return;
      }
      if (json.has("status")) {
        handleSubscribeResponse(json);
        return;
      }
      log.warn("Ignoring an unexpected user data stream message: {}", message);
    } catch (JacksonException | IllegalArgumentException e) {
      log.warn("Could not read a user data stream message: {}", message, e);
    }
  }

  @Override
  public synchronized void close() {
    connection.ifPresent(UserDataStreamConnection::close);
    connection = Optional.empty();
  }

  private void sendSubscribeRequest(UserDataStreamConnection openedConnection) {
    openedConnection.sendText(subscription.subscribeRequest());
    log.info("Binance: subscribing to the user data stream");
  }

  private void handleSubscribeResponse(JsonNode response) {
    if (response.get("status").asInt() == SUCCESS_STATUS) {
      log.info("Binance: subscribed to the user data stream, synchronizing account balances");
      synchronizer.synchronizeSoon();
      return;
    }
    JsonNode error = OrderUtil.required(response, "error");
    log.error(
        "Binance refused the user data stream subscription: {} {}",
        OrderUtil.required(error, "code").asInt(),
        OrderUtil.required(error, "msg").asString());
  }

  private void handleEvent(JsonNode event) {
    String eventType = OrderUtil.required(event, "e").asString();
    switch (eventType) {
      case ACCOUNT_POSITION_EVENT -> applyAccountPosition(event);
      case EXECUTION_REPORT_EVENT -> logExecutionReport(event);
      case STREAM_TERMINATED_EVENT -> resubscribe();
      case SERVER_SHUTDOWN_EVENT -> log.info("Binance: user data stream server is shutting down");
      default -> log.debug("Ignoring user data stream event {}", eventType);
    }
  }

  private void applyAccountPosition(JsonNode event) {
    Instant updateTime = Instant.ofEpochMilli(OrderUtil.required(event, "u").asLong());
    synchronizer.applyBalanceUpdate(updateTime, OrderUtil.accountPositionBalances(event));
    log.debug("Binance: account balances updated at {}", updateTime);
  }

  private static void logExecutionReport(JsonNode event) {
    log.info(
        "Binance: order {} {} {}",
        OrderUtil.required(event, "c").asString(),
        OrderUtil.required(event, "s").asString(),
        OrderUtil.required(event, "X").asString());
  }

  private synchronized void resubscribe() {
    log.warn("Binance: the user data stream subscription ended, subscribing again");
    connection.ifPresent(this::sendSubscribeRequest);
  }
}
