package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.order.OrderHistoryEntry;

record OrderHistoryRow(long orderId, OrderHistoryEntry order) {}
