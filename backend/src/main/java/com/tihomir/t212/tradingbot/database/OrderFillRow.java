package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.order.OrderFill;

record OrderFillRow(String clientOrderId, OrderFill fill) {}
