package net.orbis.zakum.core.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 * Shared async pool for Zakum.
 *
 * Reload safety:
 * - keep a single pool reference
 * - shut it down on plugin disable
 */
public final class Async {

  private Async() {}

  public static ExecutorService newSharedPool(Logger log) {
    ThreadFactory factory = Thread.ofVirtual()
      .name("zakum-vt-", 0)
      .uncaughtExceptionHandler((th, ex) ->
        log.warning("Uncaught in " + th.getName() + ": " + ex.getMessage())
      )
      .factory();
    return Executors.newThreadPerTaskExecutor(factory);
  }
}
