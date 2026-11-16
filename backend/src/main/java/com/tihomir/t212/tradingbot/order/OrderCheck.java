package com.tihomir.t212.tradingbot.order;

import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import java.util.Optional;

/** The outcome of the order checks: either an order to place or the reason it was refused. */
public record OrderCheck(Optional<MarketOrderRequest> order, Optional<RejectionReason> refusal) {

  public OrderCheck {
    if (order.isPresent() == refusal.isPresent()) {
      throw new IllegalArgumentException("An order check is either approved or refused");
    }
  }

  public static OrderCheck approved(MarketOrderRequest order) {
    return new OrderCheck(Optional.of(order), Optional.empty());
  }

  public static OrderCheck refused(RejectionReason reason) {
    return new OrderCheck(Optional.empty(), Optional.of(reason));
  }

  public boolean isApproved() {
    return order.isPresent();
  }
}
