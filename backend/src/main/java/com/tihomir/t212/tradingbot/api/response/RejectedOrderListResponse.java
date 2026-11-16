package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.order.model.RejectedOrder;
import java.util.List;

public record RejectedOrderListResponse(List<RejectedOrder> rejectedOrders) {}
