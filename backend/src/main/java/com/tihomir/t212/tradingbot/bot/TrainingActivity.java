package com.tihomir.t212.tradingbot.bot;

/** Lets the mode switch be refused while a training is going. */
public interface TrainingActivity {

  boolean isTrainingGoing();
}
