package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.time.Instant;

public record RejectedOrder(
    String broker,
    String ticker,
    OrderSide side,
    String orderType,
    BigDecimal amount,
    OrderAmountType amountType,
    int errorCode,
    String errorMessage,
    Instant rejectedAt) {

  static RejectedOrder of(
      String broker,
      MarketOrderRequest request,
      ExchangeRejectedRequestException rejection,
      Instant rejectedAt) {
    return new RejectedOrder(
        broker,
        request.ticker(),
        request.side(),
        MarketOrderRequest.ORDER_TYPE,
        request.amount(),
        request.amountType(),
        rejection.exchangeErrorCode(),
        rejection.getMessage(),
        rejectedAt);
  }
}
