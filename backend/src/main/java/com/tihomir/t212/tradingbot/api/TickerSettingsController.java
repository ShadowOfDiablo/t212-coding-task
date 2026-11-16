package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.api.request.TickerSettingsRequestBody;
import com.tihomir.t212.tradingbot.api.response.TickerSettingsListResponse;
import com.tihomir.t212.tradingbot.api.response.TickerSettingsResponse;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/{broker}/ticker-settings")
public class TickerSettingsController {

  private final TickerSettingsService tickerSettingsService;

  public TickerSettingsController(TickerSettingsService tickerSettingsService) {
    this.tickerSettingsService = tickerSettingsService;
  }

  @GetMapping
  public TickerSettingsListResponse getTickerSettings(@PathVariable String broker) {
    return TickerSettingsListResponse.of(tickerSettingsService.getTickerSettings(broker));
  }

  @PutMapping("/{ticker}")
  public TickerSettingsResponse assignSettings(
      @PathVariable String broker,
      @PathVariable String ticker,
      @Valid @RequestBody TickerSettingsRequestBody body) {
    return TickerSettingsResponse.of(
        tickerSettingsService.assignSettings(
            broker, ticker, body.settings().toSettingsRequest(), body.getBudget()));
  }
}
