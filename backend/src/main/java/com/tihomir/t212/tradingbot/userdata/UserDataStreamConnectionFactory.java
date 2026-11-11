package com.tihomir.t212.tradingbot.userdata;

import java.net.URI;

public interface UserDataStreamConnectionFactory {

  UserDataStreamConnection open(URI uri, UserDataStreamHandler handler);
}
