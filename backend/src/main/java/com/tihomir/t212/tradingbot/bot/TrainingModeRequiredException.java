package com.tihomir.t212.tradingbot.bot;

public class TrainingModeRequiredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TrainingModeRequiredException() {
    super("Training runs only in TRAINING mode. Switch the bot mode first.");
  }
}
