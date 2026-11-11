package com.tihomir.t212.tradingbot.userdata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JdkUserDataStreamConnection implements UserDataStreamConnection, WebSocket.Listener {

  private static final Logger log = LoggerFactory.getLogger(JdkUserDataStreamConnection.class);
  private static final CompletionStage<Void> MESSAGE_HANDLED =
      CompletableFuture.completedFuture(null);
  private static final int MESSAGES_PER_REQUEST = 1;

  private final URI uri;
  private final UserDataStreamHandler handler;
  private final HttpClient httpClient;
  private final ScheduledExecutorService reconnectScheduler;
  private final Duration connectTimeout;
  private final Duration reconnectDelay;
  private final StringBuilder messageBuffer = new StringBuilder();
  private volatile WebSocket webSocket;
  private volatile boolean closed;

  JdkUserDataStreamConnection(
      URI uri,
      UserDataStreamHandler handler,
      HttpClient httpClient,
      ScheduledExecutorService reconnectScheduler,
      Duration connectTimeout,
      Duration reconnectDelay) {
    this.uri = uri;
    this.handler = handler;
    this.httpClient = httpClient;
    this.reconnectScheduler = reconnectScheduler;
    this.connectTimeout = connectTimeout;
    this.reconnectDelay = reconnectDelay;
  }

  void connect() {
    if (closed) {
      return;
    }
    httpClient
        .newWebSocketBuilder()
        .connectTimeout(connectTimeout)
        .buildAsync(uri, this)
        .whenComplete(this::onConnectCompleted);
  }

  private void reconnect() {
    try {
      connect();
    } catch (RuntimeException e) {
      log.error("Reconnecting the user data stream to {} failed", uri.getHost(), e);
      scheduleReconnect();
    }
  }

  private void onConnectCompleted(WebSocket socket, Throwable error) {
    if (error != null) {
      log.warn("Connecting the user data stream to {} failed: {}", uri.getHost(), error.toString());
      scheduleReconnect();
    }
  }

  private void scheduleReconnect() {
    if (closed || reconnectScheduler.isShutdown()) {
      return;
    }
    log.info("Reconnecting the user data stream to {} in {}", uri.getHost(), reconnectDelay);
    try {
      reconnectScheduler.schedule(
          this::reconnect, reconnectDelay.toMillis(), TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      log.info("Not reconnecting the user data stream: the scheduler is shutting down");
    }
  }

  @Override
  public void onOpen(WebSocket socket) {
    this.webSocket = socket;
    log.info("User data stream connected to {}", uri.getHost());
    try {
      handler.onConnected(this);
    } catch (RuntimeException e) {
      log.error("Starting the user data stream on {} failed", uri.getHost(), e);
    } finally {
      socket.request(MESSAGES_PER_REQUEST);
    }
  }

  @Override
  public CompletionStage<Void> onText(WebSocket socket, CharSequence data, boolean last) {
    try {
      handleFragment(data, last);
    } catch (RuntimeException e) {
      log.error("Handling a user data message from {} failed", uri.getHost(), e);
    } finally {
      socket.request(MESSAGES_PER_REQUEST);
    }
    return MESSAGE_HANDLED;
  }

  private void handleFragment(CharSequence data, boolean last) {
    if (last && messageBuffer.isEmpty()) {
      handler.onMessage(data.toString());
      return;
    }
    messageBuffer.append(data);
    if (last) {
      String message = messageBuffer.toString();
      discardMessageBuffer();
      handler.onMessage(message);
    }
  }

  private void discardMessageBuffer() {
    messageBuffer.setLength(0);
    messageBuffer.trimToSize();
  }

  @Override
  public void sendText(String message) {
    WebSocket socket = webSocket;
    if (socket == null) {
      log.warn("Not sending to {}: the user data stream is not connected", uri.getHost());
      return;
    }
    try {
      socket.sendText(message, true).whenComplete(this::onTextSent);
    } catch (RuntimeException e) {
      log.warn("Sending to the user data stream on {} failed", uri.getHost(), e);
    }
  }

  private void onTextSent(WebSocket socket, Throwable error) {
    if (error != null) {
      log.warn("Sending to the user data stream on {} failed: {}", uri.getHost(), error.toString());
    }
  }

  @Override
  public CompletionStage<Void> onClose(WebSocket socket, int statusCode, String reason) {
    log.info("User data stream to {} closed: {} {}", uri.getHost(), statusCode, reason);
    resetAndReconnect();
    return MESSAGE_HANDLED;
  }

  @Override
  public void onError(WebSocket socket, Throwable error) {
    log.warn("User data stream to {} failed: {}", uri.getHost(), error.toString());
    resetAndReconnect();
  }

  private void resetAndReconnect() {
    webSocket = null;
    discardMessageBuffer();
    scheduleReconnect();
  }

  @Override
  public boolean isOpen() {
    WebSocket socket = webSocket;
    if (socket == null) {
      return false;
    }
    return !socket.isInputClosed();
  }

  @Override
  public void close() {
    closed = true;
    WebSocket socket = webSocket;
    if (socket != null) {
      socket.sendClose(WebSocket.NORMAL_CLOSURE, "").whenComplete(this::onCloseSent);
    }
  }

  private void onCloseSent(WebSocket socket, Throwable error) {
    if (error != null) {
      log.debug("Closing the user data stream to {} failed: {}", uri.getHost(), error.toString());
    }
  }
}
