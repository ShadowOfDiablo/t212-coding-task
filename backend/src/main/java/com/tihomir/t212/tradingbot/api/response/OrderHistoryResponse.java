package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.order.model.OrderHistoryEntry;
import java.util.List;

public record OrderHistoryResponse(List<OrderHistoryEntry> orders) {}
