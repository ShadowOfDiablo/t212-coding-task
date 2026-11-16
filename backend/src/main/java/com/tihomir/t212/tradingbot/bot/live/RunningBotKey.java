package com.tihomir.t212.tradingbot.bot.live;

/** One live bot runs per broker and ticker. */
record RunningBotKey(String broker, String ticker) {}
