package com.tihomir.t212.tradingbot.api;

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

  public BotModeController(BotModeService botModeService) {
    this.botModeService = botModeService;
  }

  @GetMapping
  public BotModeSetting botMode() {
    return new BotModeSetting(botModeService.botMode());
  }

  @PutMapping
  public BotModeSetting setBotMode(@RequestBody BotModeSetting setting) {
    botModeService.setBotMode(setting.mode());
    return new BotModeSetting(botModeService.botMode());
  }
}
