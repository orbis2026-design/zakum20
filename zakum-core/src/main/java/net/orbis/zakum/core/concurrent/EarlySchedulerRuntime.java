package net.orbis.zakum.core.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pre-plugin bootstrap state for async executor initialization.
 * The bootstrapper initializes this before worlds load; ZakumPlugin claims it on enable.
 */
public final class EarlySchedulerRuntime {

  private static final AtomicReference<ExecutorService> EARLY_EXECUTOR = new AtomicReference<>();

  private EarlySchedulerRuntime() {}

  public static void initializeEarlyExecutor() {
    EARLY_EXECUTOR.updateAndGet(existing -> existing != null ? existing : Executors.newVirtualThreadPerTaskExecutor());
  }

  public static ExecutorService claimOrCreateExecutor() {
    ExecutorService claimed = EARLY_EXECUTOR.getAndSet(null);
    if (claimed != null) return claimed;
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
