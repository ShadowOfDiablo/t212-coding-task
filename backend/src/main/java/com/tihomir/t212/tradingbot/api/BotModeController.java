package com.tihomir.t212.tradingbot.api;

import com.tihomir.t212.tradingbot.bot.TradingBotService;
import com.tihomir.t212.tradingbot.mode.BotModeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot/mode")
public class BotModeController {

  private final BotModeService botModeService;
  private final TradingBotService tradingBotService;

  public BotModeController(BotModeService botModeService, TradingBotService tradingBotService) {
    this.botModeService = botModeService;
    this.tradingBotService = tradingBotService;
  }

  @GetMapping
  public BotModeSetting botMode() {
    return new BotModeSetting(botModeService.botMode());
  }

  /** Refused with 409 while a live bot runs. */
  @PutMapping
  public BotModeSetting setBotMode(@RequestBody BotModeSetting setting) {
    tradingBotService.switchBotMode(setting.mode());
    return new BotModeSetting(botModeService.botMode());
  }
}
