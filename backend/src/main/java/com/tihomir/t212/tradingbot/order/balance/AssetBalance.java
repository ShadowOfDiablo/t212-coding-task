package com.tihomir.t212.tradingbot.order.balance;

import java.math.BigDecimal;

public record AssetBalance(String asset, BigDecimal free, BigDecimal locked) {}
