package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.api.response.AccountBalanceResponse;
import com.tihomir.t212.tradingbot.api.response.OpenOrderListResponse;
import com.tihomir.t212.tradingbot.api.response.OrderHistoryResponse;
import com.tihomir.t212.tradingbot.api.response.RejectedOrderListResponse;
import com.tihomir.t212.tradingbot.order.OrderService;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/{broker}")
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public ExecutedOrder placeMarketOrder(
      @PathVariable String broker, @RequestBody MarketOrderRequest request) {
    return orderService.placeMarketOrder(broker, request);
  }

  @GetMapping("/balance")
  public AccountBalanceResponse getAccountBalances(@PathVariable String broker) {
    return new AccountBalanceResponse(orderService.getAccountBalances(broker).balances());
  }

  @GetMapping("/orders/open")
  public OpenOrderListResponse getOpenOrders(@PathVariable String broker) {
    return new OpenOrderListResponse(orderService.getOpenOrders(broker).openOrders());
  }

  @GetMapping("/orders/history")
  public OrderHistoryResponse getOrderHistory(@PathVariable String broker) {
    return new OrderHistoryResponse(orderService.getOrderHistory(broker).orders());
  }

  @GetMapping("/orders/rejected")
  public RejectedOrderListResponse getRejectedOrders(@PathVariable String broker) {
    return new RejectedOrderListResponse(orderService.getRejectedOrders(broker).rejectedOrders());
  }
}
