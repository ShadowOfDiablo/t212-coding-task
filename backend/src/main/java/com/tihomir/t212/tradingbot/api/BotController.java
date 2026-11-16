package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.api.request.AlgorithmSettingsBody;
import com.tihomir.t212.tradingbot.api.response.LiveBotStatusListResponse;
import com.tihomir.t212.tradingbot.api.response.LiveBotStatusResponse;
import com.tihomir.t212.tradingbot.api.response.RunDetailsResponse;
import com.tihomir.t212.tradingbot.bot.live.TradingBotService;
import com.tihomir.t212.tradingbot.bot.run.AlgorithmRunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The live bots of a broker, one per ticker, each trading with its ticker's settings. */
@RestController
@RequestMapping("/api/brokers/{broker}/bots")
public class BotController {

  private final TradingBotService tradingBotService;
  private final AlgorithmRunService runService;

  public BotController(TradingBotService tradingBotService, AlgorithmRunService runService) {
    this.tradingBotService = tradingBotService;
    this.runService = runService;
  }

  @GetMapping
  public LiveBotStatusListResponse getBotStatuses(@PathVariable String broker) {
    return LiveBotStatusListResponse.of(tradingBotService.getBotStatuses(broker));
  }

  @PostMapping("/start-all")
  public LiveBotStatusListResponse startAllBots(@PathVariable String broker) {
    return LiveBotStatusListResponse.of(tradingBotService.startAllBots(broker));
  }

  @PostMapping("/stop-all")
  public LiveBotStatusListResponse stopAllBots(@PathVariable String broker) {
    tradingBotService.stopAllBots(broker);
    return LiveBotStatusListResponse.of(tradingBotService.getBotStatuses(broker));
  }

  @PostMapping("/{ticker}/start")
  @ResponseStatus(HttpStatus.CREATED)
  public LiveBotStatusResponse startBot(@PathVariable String broker, @PathVariable String ticker) {
    tradingBotService.startBot(broker, ticker);
    return LiveBotStatusResponse.of(tradingBotService.getBotStatus(broker, ticker));
  }

  @PostMapping("/{ticker}/stop")
  public RunDetailsResponse stopBot(@PathVariable String broker, @PathVariable String ticker) {
    long runId = tradingBotService.stopBot(broker, ticker);
    return RunDetailsResponse.of(runService.getRun(broker, runId));
  }

  @PostMapping("/{ticker}/pause")
  public LiveBotStatusResponse pauseBot(@PathVariable String broker, @PathVariable String ticker) {
    return LiveBotStatusResponse.running(tradingBotService.pauseBot(broker, ticker));
  }

  @PostMapping("/{ticker}/resume")
  public LiveBotStatusResponse resumeBot(@PathVariable String broker, @PathVariable String ticker) {
    return LiveBotStatusResponse.running(tradingBotService.resumeBot(broker, ticker));
  }

  @PutMapping("/{ticker}/current-parameters")
  public LiveBotStatusResponse updateCurrentParameters(
      @PathVariable String broker,
      @PathVariable String ticker,
      @Valid @RequestBody AlgorithmSettingsBody body) {
    return LiveBotStatusResponse.running(
        tradingBotService.updateCurrentParameters(broker, ticker, body.toSettingsRequest()));
  }
}
