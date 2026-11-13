package com.tihomir.t212.tradingbot.position;

import java.util.List;

/** Open lots, oldest first. */
public record OpenLotList(List<OpenLot> lots) {

  public static final OpenLotList NONE = new OpenLotList(List.of());

  public OpenLotList {
    lots = List.copyOf(lots);
  }
}
