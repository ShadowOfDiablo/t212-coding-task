package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.order.OpenOrder;
import java.util.List;

public record OpenOrderListResponse(List<OpenOrder> openOrders) {}
