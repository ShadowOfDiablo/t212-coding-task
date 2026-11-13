package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.algorithm.InvalidAlgorithmSettingsException;
import com.tihomir.t212.tradingbot.algorithm.UnknownAlgorithmException;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.marketdata.history.CandleDownloadFailedException;
import com.tihomir.t212.tradingbot.marketdata.history.CandleRangeTooLargeException;
import com.tihomir.t212.tradingbot.order.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.OrderStatusUnknownException;
import com.tihomir.t212.tradingbot.order.TickerNotTradedException;
import com.tihomir.t212.tradingbot.order.TradingNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(BrokerNotFoundException.class)
  ProblemDetail brokerNotFound(BrokerNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, e);
  }

  @ExceptionHandler({
    TickerNotTradedException.class,
    CandleRangeTooLargeException.class,
    UnknownAlgorithmException.class,
    InvalidAlgorithmSettingsException.class,
    IllegalArgumentException.class
  })
  ProblemDetail invalidRequest(RuntimeException e) {
    return problem(HttpStatus.BAD_REQUEST, e);
  }

  @ExceptionHandler(TradingNotConfiguredException.class)
  ProblemDetail tradingNotConfigured(TradingNotConfiguredException e) {
    return problem(HttpStatus.CONFLICT, e);
  }

  @ExceptionHandler(ExchangeRejectedRequestException.class)
  ProblemDetail exchangeRejected(ExchangeRejectedRequestException e) {
    ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, e);
    problem.setProperty("exchangeErrorCode", e.exchangeErrorCode());
    return problem;
  }

  @ExceptionHandler(OrderStatusUnknownException.class)
  ProblemDetail orderStatusUnknown(OrderStatusUnknownException e) {
    log.error("Order {} has an unknown status: {}", e.clientOrderId(), e.getMessage(), e);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            e.getMessage() + ". Check the order on Binance by its client order id.");
    problem.setProperty("clientOrderId", e.clientOrderId());
    return problem;
  }

  @ExceptionHandler(ExchangeUnavailableException.class)
  ProblemDetail exchangeUnavailable(ExchangeUnavailableException e) {
    log.error("Exchange request failed: {}", e.getMessage(), e);
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
  }

  @ExceptionHandler(CandleDownloadFailedException.class)
  ProblemDetail candleDownloadFailed(CandleDownloadFailedException e) {
    log.error("Candle download failed: {}", e.getMessage(), e);
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
  }

  @ExceptionHandler(DataAccessException.class)
  ProblemDetail databaseUnavailable(DataAccessException e) {
    log.error("Database request failed: {}", e.getMessage(), e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "The database is unavailable");
  }

  private static ProblemDetail problem(HttpStatus status, RuntimeException e) {
    log.warn("Request refused with {}: {}", status.value(), e.getMessage());
    return ProblemDetail.forStatusAndDetail(status, e.getMessage());
  }
}
