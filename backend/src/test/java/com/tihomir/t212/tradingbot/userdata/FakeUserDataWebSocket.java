package com.tihomir.t212.tradingbot.userdata;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class FakeUserDataWebSocket implements WebSocket {

  private final List<String> sentTexts = new ArrayList<>();
  private long requestedMessages;

  long requestedMessages() {
    return requestedMessages;
  }

  List<String> sentTexts() {
    return List.copyOf(sentTexts);
  }

  @Override
  public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
    sentTexts.add(data.toString());
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public void request(long n) {
    requestedMessages += n;
  }

  @Override
  public String getSubprotocol() {
    return "";
  }

  @Override
  public boolean isOutputClosed() {
    return false;
  }

  @Override
  public boolean isInputClosed() {
    return false;
  }

  @Override
  public void abort() {}
}
