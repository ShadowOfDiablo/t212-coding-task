package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import java.util.List;

public record AccountBalanceResponse(List<AssetBalance> balances) {}
