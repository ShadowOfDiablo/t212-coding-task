package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.order.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.OrderService;
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
  public AccountBalanceResponse accountBalance(@PathVariable String broker) {
    return new AccountBalanceResponse(orderService.accountBalances(broker));
  }

  @GetMapping("/orders/open")
  public OpenOrderListResponse getOpenOrders(@PathVariable String broker) {
    return new OpenOrderListResponse(orderService.getOpenOrders(broker));
  }

  @GetMapping("/orders/history")
  public OrderHistoryResponse getOrderHistory(@PathVariable String broker) {
    return new OrderHistoryResponse(orderService.getOrderHistory(broker));
  }
}
