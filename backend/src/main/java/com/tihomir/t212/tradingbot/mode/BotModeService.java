package com.tihomir.t212.tradingbot.mode;

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BotModeService {

  private static final Logger log = LoggerFactory.getLogger(BotModeService.class);

  private final AtomicReference<BotMode> botMode;

  public BotModeService(@Value("${bot.mode:TRADING}") BotMode initialBotMode) {
    this.botMode = new AtomicReference<>(initialBotMode);
  }

  public BotMode botMode() {
    return botMode.get();
  }

  public void setBotMode(BotMode newBotMode) {
    if (newBotMode == null) {
      throw new IllegalArgumentException("The bot mode must be TRADING or TRAINING");
    }
    BotMode previousBotMode = botMode.getAndSet(newBotMode);
    log.info("Bot mode switched from {} to {}", previousBotMode, newBotMode);
  }
}
