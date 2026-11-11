package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/{broker}")
public class MarketDataController {

  private final TickerPriceService tickerPriceService;

  public MarketDataController(TickerPriceService tickerPriceService) {
    this.tickerPriceService = tickerPriceService;
  }

  @GetMapping("/tickers")
  public TickerPriceListResponse getTickerPrices(@PathVariable String broker) {
    return new TickerPriceListResponse(tickerPriceService.getTickerPrices(broker).tickers());
  }
}
