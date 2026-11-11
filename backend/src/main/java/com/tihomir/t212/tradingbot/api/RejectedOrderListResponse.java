package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.order.RejectedOrder;
import java.util.List;

public record RejectedOrderListResponse(List<RejectedOrder> rejectedOrders) {}
