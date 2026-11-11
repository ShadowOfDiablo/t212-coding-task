package com.tihomir.t212.tradingbot.userdata;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class FakeUserDataStreamConnectionFactory implements UserDataStreamConnectionFactory {

  private final List<URI> openedUris = new ArrayList<>();
  private final FakeUserDataStreamConnection connection = new FakeUserDataStreamConnection();

  @Override
  public UserDataStreamConnection open(URI uri, UserDataStreamHandler handler) {
    openedUris.add(uri);
    return connection;
  }

  public List<URI> openedUris() {
    return List.copyOf(openedUris);
  }

  public FakeUserDataStreamConnection connection() {
    return connection;
  }
}
