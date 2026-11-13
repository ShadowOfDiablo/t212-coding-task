package com.tihomir.t212.tradingbot.position;

import java.util.List;

/** Position writes in the order they happened. */
public record PositionWriteList(List<PositionWrite> writes) {

  public static final PositionWriteList NONE = new PositionWriteList(List.of());

  public PositionWriteList {
    writes = List.copyOf(writes);
  }
}
