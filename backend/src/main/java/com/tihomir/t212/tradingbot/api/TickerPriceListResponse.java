package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPrice;
import java.util.List;

public record TickerPriceListResponse(List<TickerPrice> tickers) {}
