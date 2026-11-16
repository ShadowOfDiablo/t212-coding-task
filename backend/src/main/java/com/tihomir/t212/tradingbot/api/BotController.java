package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.api.request.AlgorithmSettingsBody;
import com.tihomir.t212.tradingbot.api.request.LiveRunRequestBody;
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

@RestController
@RequestMapping("/api/brokers/{broker}/bot")
public class BotController {

  private final TradingBotService tradingBotService;
  private final AlgorithmRunService runService;

  public BotController(TradingBotService tradingBotService, AlgorithmRunService runService) {
    this.tradingBotService = tradingBotService;
    this.runService = runService;
  }

  @GetMapping
  public LiveBotStatusResponse getBotStatus(@PathVariable String broker) {
    return LiveBotStatusResponse.of(tradingBotService.getBotStatus(broker));
  }

  @PostMapping("/start")
  @ResponseStatus(HttpStatus.CREATED)
  public LiveBotStatusResponse startBot(
      @PathVariable String broker, @Valid @RequestBody LiveRunRequestBody body) {
    tradingBotService.startBot(body.toLiveRunRequest(broker));
    return LiveBotStatusResponse.of(tradingBotService.getBotStatus(broker));
  }

  @PostMapping("/stop")
  public RunDetailsResponse stopBot(@PathVariable String broker) {
    long runId = tradingBotService.stopBot(broker);
    return RunDetailsResponse.of(runService.getRun(broker, runId));
  }

  @PostMapping("/pause")
  public LiveBotStatusResponse pauseBot(@PathVariable String broker) {
    return LiveBotStatusResponse.running(tradingBotService.pauseBot(broker));
  }

  @PostMapping("/resume")
  public LiveBotStatusResponse resumeBot(@PathVariable String broker) {
    return LiveBotStatusResponse.running(tradingBotService.resumeBot(broker));
  }

  @PutMapping("/current-parameters")
  public LiveBotStatusResponse updateCurrentParameters(
      @PathVariable String broker, @Valid @RequestBody AlgorithmSettingsBody body) {
    return LiveBotStatusResponse.running(
        tradingBotService.updateCurrentParameters(broker, body.toSettingsRequest()));
  }
}
