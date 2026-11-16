package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.api.request.FollowTickerRequestBody;
import com.tihomir.t212.tradingbot.api.response.TickerPriceListResponse;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.FollowedTickerService;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/{broker}")
public class MarketDataController {

  private final TickerPriceService tickerPriceService;
  private final FollowedTickerService followedTickerService;

  public MarketDataController(
      TickerPriceService tickerPriceService, FollowedTickerService followedTickerService) {
    this.tickerPriceService = tickerPriceService;
    this.followedTickerService = followedTickerService;
  }

  @GetMapping("/tickers")
  public TickerPriceListResponse getTickerPrices(@PathVariable String broker) {
    return new TickerPriceListResponse(tickerPriceService.getTickerPrices(broker).tickers());
  }

  @PostMapping("/tickers")
  @ResponseStatus(HttpStatus.CREATED)
  public TickerPriceListResponse followTicker(
      @PathVariable String broker, @Valid @RequestBody FollowTickerRequestBody body) {
    followedTickerService.followTicker(broker, body.getNormalizedTicker());
    return getTickerPrices(broker);
  }
}
