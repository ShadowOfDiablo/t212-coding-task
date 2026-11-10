package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;

public record AssetBalance(String asset, BigDecimal free, BigDecimal locked) {}
