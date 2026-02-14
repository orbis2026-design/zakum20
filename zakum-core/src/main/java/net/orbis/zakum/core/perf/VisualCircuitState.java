package net.orbis.zakum.core.perf;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared state used by high-cost visual ACE effects.
 */
public final class VisualCircuitState {

  private static final AtomicBoolean OPEN = new AtomicBoolean(false);
  private static final AtomicReference<String> REASON = new AtomicReference<>("");
  private static final AtomicLong CHANGED_AT_MS = new AtomicLong(0L);

  private VisualCircuitState() {}

  public static void open(String reason) {
    OPEN.set(true);
    REASON.set(reason == null ? "" : reason);
    CHANGED_AT_MS.set(System.currentTimeMillis());
  }

  public static void close() {
    OPEN.set(false);
    REASON.set("");
    CHANGED_AT_MS.set(System.currentTimeMillis());
  }

  public static boolean isOpen() {
    return OPEN.get();
  }

  public static String reason() {
    return REASON.get();
  }

  public static long changedAtMs() {
    return CHANGED_AT_MS.get();
  }
}
