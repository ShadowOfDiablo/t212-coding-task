package com.tihomir.t212.tradingbot.marketdata.stream;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

final class FakeWebSocket implements WebSocket {

  private long requestedMessages;
  private boolean pongFails;

  long requestedMessages() {
    return requestedMessages;
  }

  void failPongs() {
    pongFails = true;
  }

  @Override
  public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
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
    if (pongFails) {
      throw new IllegalStateException("output closed");
    }
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
