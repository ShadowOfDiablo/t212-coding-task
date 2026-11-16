package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.position.StoredPositionList;
import java.util.List;

public record PositionListResponse(List<PositionResponse> positions) {

  public static PositionListResponse of(StoredPositionList positions) {
    return new PositionListResponse(
        positions.positions().stream().map(PositionResponse::of).toList());
  }
}
