package com.tihomir.t212.tradingbot.bot.tuning;

/**
 * One setting the tuner may change, with the range it draws from. A category's values are the
 * indexes of its choices, from 0 to the number of choices - 1.
 */
record TuningDimension(String name, TuningDimensionKind kind, double minimum, double maximum) {

  double width() {
    return maximum - minimum;
  }
}
