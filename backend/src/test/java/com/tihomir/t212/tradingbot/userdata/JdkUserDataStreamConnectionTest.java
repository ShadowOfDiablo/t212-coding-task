package com.tihomir.t212.tradingbot.userdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JdkUserDataStreamConnectionTest {

  private static final String SUBSCRIBE = "{\"method\":\"subscribe\"}";

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final FakeUserDataWebSocket socket = new FakeUserDataWebSocket();
  private final List<String> received = new ArrayList<>();

  @AfterEach
  void shutDown() {
    scheduler.shutdownNow();
    httpClient.close();
  }

  @Test
  void letsTheHandlerSubscribeOnceConnected() {
    JdkUserDataStreamConnection connection = connection(new SubscribingHandler(received));

    connection.onOpen(socket);

    assertThat(socket.sentTexts()).containsExactly(SUBSCRIBE);
    assertThat(socket.requestedMessages()).isEqualTo(1);
  }

  @Test
  void keepsReadingWhenSubscribingFails() {
    JdkUserDataStreamConnection connection = connection(new FailingHandler());

    assertThatCode(() -> connection.onOpen(socket)).doesNotThrowAnyException();
    assertThat(socket.requestedMessages()).isEqualTo(1);
  }

  @Test
  void joinsFragmentsIntoOneMessage() {
    JdkUserDataStreamConnection connection = connection(new SubscribingHandler(received));

    connection.onText(socket, "{\"event\":", false);
    connection.onText(socket, "{}}", true);
    connection.onText(socket, "next", true);

    assertThat(received).containsExactly("{\"event\":{}}", "next");
    assertThat(socket.requestedMessages()).isEqualTo(3);
  }

  @Test
  void keepsReadingWhenTheHandlerFails() {
    JdkUserDataStreamConnection connection = connection(new FailingHandler());

    assertThatCode(() -> connection.onText(socket, "message", true)).doesNotThrowAnyException();
    assertThat(socket.requestedMessages()).isEqualTo(1);
  }

  @Test
  void sendingBeforeConnectingDoesNotThrow() {
    JdkUserDataStreamConnection connection = connection(new SubscribingHandler(received));

    assertThatCode(() -> connection.sendText(SUBSCRIBE)).doesNotThrowAnyException();
    assertThat(connection.isOpen()).isFalse();
  }

  @Test
  void closingAfterTheSchedulerStoppedDoesNotThrow() {
    JdkUserDataStreamConnection connection = connection(new SubscribingHandler(received));
    scheduler.shutdownNow();

    assertThatCode(() -> connection.onClose(socket, 1000, "bye")).doesNotThrowAnyException();
    assertThatCode(() -> connection.onError(socket, new RuntimeException("boom")))
        .doesNotThrowAnyException();
  }

  private JdkUserDataStreamConnection connection(UserDataStreamHandler handler) {
    return new JdkUserDataStreamConnection(
        URI.create("wss://user-data.test"),
        handler,
        httpClient,
        scheduler,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }
}
