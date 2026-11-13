package com.tihomir.t212.tradingbot.position;

import java.util.List;

public record StoredPositionList(List<StoredPosition> positions) {

  public StoredPositionList {
    positions = List.copyOf(positions);
  }
}
