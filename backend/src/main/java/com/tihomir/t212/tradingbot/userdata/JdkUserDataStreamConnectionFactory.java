package com.tihomir.t212.tradingbot.userdata;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.stereotype.Component;

@Component
public class JdkUserDataStreamConnectionFactory
    implements UserDataStreamConnectionFactory, AutoCloseable {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration RECONNECT_DELAY = Duration.ofSeconds(5);

  private final HttpClient httpClient;
  private final ScheduledExecutorService reconnectScheduler;

  public JdkUserDataStreamConnectionFactory() {
    this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(this::newDaemonThread);
  }

  @Override
  public UserDataStreamConnection open(URI uri, UserDataStreamHandler handler) {
    JdkUserDataStreamConnection connection =
        new JdkUserDataStreamConnection(
            uri, handler, httpClient, reconnectScheduler, CONNECT_TIMEOUT, RECONNECT_DELAY);
    connection.connect();
    return connection;
  }

  @Override
  public void close() {
    reconnectScheduler.shutdownNow();
    httpClient.close();
  }

  private Thread newDaemonThread(Runnable runnable) {
    Thread thread = new Thread(runnable, "user-data-stream-reconnect");
    thread.setDaemon(true);
    return thread;
  }
}
