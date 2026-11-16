package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.order.model.OpenOrder;
import java.util.List;

public record OpenOrderListResponse(List<OpenOrder> openOrders) {}
