package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.order.OrderHistoryEntry;
import java.util.List;

public record OrderHistoryResponse(List<OrderHistoryEntry> orders) {}
