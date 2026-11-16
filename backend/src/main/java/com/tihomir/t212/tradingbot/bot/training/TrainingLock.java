package com.tihomir.t212.tradingbot.bot.training;

import com.tihomir.t212.tradingbot.bot.exception.TrainingAlreadyRunningException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** One training or tuning at a time: both take this lock before they record anything. */
@Component
public class TrainingLock {

  private final AtomicBoolean locked = new AtomicBoolean();

  /**
   * @throws TrainingAlreadyRunningException while a training or tuning holds the lock
   */
  public void lock() {
    if (!locked.compareAndSet(false, true)) {
      throw new TrainingAlreadyRunningException();
    }
  }

  public void unlock() {
    locked.set(false);
  }

  boolean isLocked() {
    return locked.get();
  }
}
