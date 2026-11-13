package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.position.Position;
import com.tihomir.t212.tradingbot.position.PositionStatus;
import com.tihomir.t212.tradingbot.position.StoredPosition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * @param realisedPnlPercent the realised P&L as a share of what its buys cost
 * @param durationSeconds from the first buy to the close; empty while open
 */
public record PositionResponse(
    long id,
    long runId,
    String ticker,
    PositionStatus status,
    Instant openedAt,
    Optional<Instant> closedAt,
    Optional<Long> durationSeconds,
    BigDecimal cost,
    BigDecimal realisedPnl,
    BigDecimal realisedPnlPercent,
    BigDecimal mae,
    BigDecimal mfe) {

  private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
  private static final int PERCENT_SCALE = 4;

  static PositionResponse of(StoredPosition stored) {
    Position position = stored.position();
    return new PositionResponse(
        stored.id(),
        position.runId(),
        position.ticker(),
        position.status(),
        position.openedAt(),
        position.closedAt(),
        position
            .closedAt()
            .map(closedAt -> Duration.between(position.openedAt(), closedAt).toSeconds()),
        position.cost(),
        position.realisedPnl(),
        percentOfCost(position),
        position.mae(),
        position.mfe());
  }

  private static BigDecimal percentOfCost(Position position) {
    if (position.cost().signum() == 0) {
      return BigDecimal.ZERO;
    }
    return position
        .realisedPnl()
        .multiply(PERCENT)
        .divide(position.cost(), PERCENT_SCALE, RoundingMode.HALF_EVEN);
  }
}
