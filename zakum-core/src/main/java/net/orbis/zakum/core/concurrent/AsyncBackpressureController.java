package net.orbis.zakum.core.concurrent;

import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Bounded async dispatcher for virtual-thread executors.
 *
 * Guarantees:
 * - caps concurrently running tasks
 * - caps queued tasks
 * - never executes caller-runs fallback on the primary thread
 */
final class AsyncBackpressureController {

  private final ExecutorService delegate;
  private final boolean configuredEnabled;
  private final int maxInFlight;
  private final int maxQueue;
  private final boolean callerRunsOffMainThread;
  private final Logger logger;
  private final MetricsMonitor metrics;

  private final AtomicBoolean runtimeEnabled = new AtomicBoolean(true);
  private final AtomicInteger inFlight = new AtomicInteger(0);
  private final AtomicInteger queued = new AtomicInteger(0);
  private final ConcurrentLinkedQueue<Runnable> backlog = new ConcurrentLinkedQueue<>();
  private final AtomicLong lastRejectAtMs = new AtomicLong(0L);
  private final AtomicLong lastCallerRunAtMs = new AtomicLong(0L);
  private final AtomicLong lastQueueAtMs = new AtomicLong(0L);
  private final LongAdder submitted = new LongAdder();
  private final LongAdder executed = new LongAdder();
  private final LongAdder queuedTasks = new LongAdder();
  private final LongAdder rejected = new LongAdder();
  private final LongAdder callerRuns = new LongAdder();
  private final LongAdder drainRuns = new LongAdder();

  AsyncBackpressureController(
    ExecutorService delegate,
    ZakumSettings.Operations.Async cfg,
    Logger logger,
    MetricsMonitor metrics
  ) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.metrics = metrics;
    this.configuredEnabled = cfg != null && cfg.enabled();
    this.maxInFlight = cfg == null ? 4096 : Math.max(1, cfg.maxInFlight());
    this.maxQueue = cfg == null ? 16384 : Math.max(0, cfg.maxQueue());
    this.callerRunsOffMainThread = cfg != null && cfg.callerRunsOffMainThread();
  }

  void execute(Runnable task) {
    if (task == null) return;
    submitted.increment();
    if (!enabled()) {
      submit(task);
      return;
    }
    if (trySubmit(task)) return;
    if (offerToQueue(task)) return;
    if (callerRunsOffMainThread && !Bukkit.isPrimaryThread()) {
      callerRuns.increment();
      lastCallerRunAtMs.set(System.currentTimeMillis());
      if (metrics != null) metrics.recordAction("async_caller_runs");
      runTask(task);
      return;
    }
    rejected.increment();
    lastRejectAtMs.set(System.currentTimeMillis());
    if (metrics != null) metrics.recordAction("async_rejected");
    logger.warning("Async backpressure rejection (inFlight=" + inFlight.get() + ", queued=" + queued.get() + ")");
  }

  Snapshot snapshot() {
    return new Snapshot(
      configuredEnabled,
      runtimeEnabled.get(),
      enabled(),
      maxInFlight,
      maxQueue,
      callerRunsOffMainThread,
      inFlight.get(),
      queued.get(),
      submitted.sum(),
      executed.sum(),
      queuedTasks.sum(),
      rejected.sum(),
      callerRuns.sum(),
      drainRuns.sum(),
      lastQueueAtMs.get(),
      lastRejectAtMs.get(),
      lastCallerRunAtMs.get()
    );
  }

  boolean configuredEnabled() {
    return configuredEnabled;
  }

  boolean runtimeEnabled() {
    return runtimeEnabled.get();
  }

  boolean enabled() {
    return configuredEnabled && runtimeEnabled.get();
  }

  void setRuntimeEnabled(boolean enabled) {
    runtimeEnabled.set(enabled);
  }

  private boolean offerToQueue(Runnable task) {
    while (true) {
      int current = queued.get();
      if (current >= maxQueue) return false;
      if (queued.compareAndSet(current, current + 1)) {
        backlog.offer(task);
        queuedTasks.increment();
        lastQueueAtMs.set(System.currentTimeMillis());
        if (metrics != null) metrics.recordAction("async_queued");
        return true;
      }
    }
  }

  private boolean trySubmit(Runnable task) {
    while (true) {
      int current = inFlight.get();
      if (current >= maxInFlight) return false;
      if (inFlight.compareAndSet(current, current + 1)) {
        submit(task);
        return true;
      }
    }
  }

  private void submit(Runnable task) {
    try {
      delegate.execute(() -> {
        try {
          runTask(task);
        } finally {
          if (enabled()) {
            inFlight.decrementAndGet();
            drain();
          }
        }
      });
    } catch (Throwable ex) {
      if (enabled()) {
        inFlight.decrementAndGet();
      }
      rejected.increment();
      lastRejectAtMs.set(System.currentTimeMillis());
      if (metrics != null) metrics.recordAction("async_submit_failure");
      logger.warning("Async submit failed: " + ex.getMessage());
    }
  }

  private void drain() {
    if (!enabled()) return;
    while (true) {
      int currentInFlight = inFlight.get();
      if (currentInFlight >= maxInFlight) return;
      Runnable next = backlog.poll();
      if (next == null) return;
      queued.updateAndGet(v -> Math.max(0, v - 1));
      if (!inFlight.compareAndSet(currentInFlight, currentInFlight + 1)) {
        backlog.offer(next);
        queued.incrementAndGet();
        return;
      }
      drainRuns.increment();
      submit(next);
    }
  }

  private void runTask(Runnable task) {
    try {
      task.run();
    } finally {
      executed.increment();
    }
  }

  record Snapshot(
    boolean configuredEnabled,
    boolean runtimeEnabled,
    boolean enabled,
    int maxInFlight,
    int maxQueue,
    boolean callerRunsOffMainThread,
    int inFlight,
    int queued,
    long submitted,
    long executed,
    long queuedTasks,
    long rejected,
    long callerRuns,
    long drainRuns,
    long lastQueueAtMs,
    long lastRejectAtMs,
    long lastCallerRunAtMs
  ) {}
}
