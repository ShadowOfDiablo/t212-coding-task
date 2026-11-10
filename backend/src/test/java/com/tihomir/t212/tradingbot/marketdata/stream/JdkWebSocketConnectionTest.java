package com.tihomir.t212.tradingbot.marketdata.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JdkWebSocketConnectionTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final FakeWebSocket socket = new FakeWebSocket();
  private final List<String> received = new ArrayList<>();

  @AfterEach
  void shutDown() {
    scheduler.shutdownNow();
    httpClient.close();
  }

  @Test
  void joinsFragmentsIntoOneMessage() {
    JdkWebSocketConnection connection = connection(this::record);

    connection.onText(socket, "{\"a\":", false);
    connection.onText(socket, "1}", true);

    assertThat(received).containsExactly("{\"a\":1}");
    assertThat(socket.requestedMessages()).isEqualTo(2);
  }

  @Test
  void keepsReadingWhenTheHandlerFails() {
    JdkWebSocketConnection connection = connection(this::fail);

    assertThatCode(() -> connection.onText(socket, "message", true)).doesNotThrowAnyException();
    assertThat(socket.requestedMessages()).isEqualTo(1);
  }

  @Test
  void dropsOversizedMessagesAndKeepsReading() {
    JdkWebSocketConnection connection = connection(this::record);

    connection.onText(socket, "x".repeat(1_000_001), false);
    connection.onText(socket, "next", true);

    assertThat(received).containsExactly("next");
    assertThat(socket.requestedMessages()).isEqualTo(2);
  }

  @Test
  void keepsReadingWhenAnsweringAPingFails() {
    JdkWebSocketConnection connection = connection(this::record);
    socket.failPongs();

    assertThatCode(() -> connection.onPing(socket, ByteBuffer.allocate(0)))
        .doesNotThrowAnyException();
    assertThat(socket.requestedMessages()).isEqualTo(1);
  }

  @Test
  void closingAfterTheSchedulerStoppedDoesNotThrow() {
    JdkWebSocketConnection connection = connection(this::record);
    scheduler.shutdownNow();

    assertThatCode(() -> connection.onClose(socket, 1000, "bye")).doesNotThrowAnyException();
    assertThatCode(() -> connection.onError(socket, new RuntimeException("boom")))
        .doesNotThrowAnyException();
  }

  private JdkWebSocketConnection connection(MarketDataMessageHandler handler) {
    return new JdkWebSocketConnection(
        URI.create("wss://stream.test"),
        handler,
        httpClient,
        scheduler,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }

  private void record(String message, Instant receivedAt) {
    received.add(message);
  }

  private void fail(String message, Instant receivedAt) {
    throw new IllegalStateException("handler failed");
  }
}
