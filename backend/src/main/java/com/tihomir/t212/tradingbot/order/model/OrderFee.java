package com.tihomir.t212.tradingbot.order.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** The total fee of an order, in the one currency all its fills paid in. */
public record OrderFee(BigDecimal amount, String currency) {

  /** Empty when there are no fills, or when the fills paid in more than one currency. */
  public static Optional<OrderFee> of(List<OrderFill> fills) {
    List<String> currencies = fills.stream().map(OrderFill::feeCurrency).distinct().toList();
    if (currencies.size() != 1) {
      return Optional.empty();
    }
    BigDecimal amount =
        fills.stream().map(OrderFill::feeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return Optional.of(new OrderFee(amount, currencies.getFirst()));
  }
}
