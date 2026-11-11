package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.order.AssetBalance;
import java.util.List;

public record AccountBalanceResponse(List<AssetBalance> balances) {}
