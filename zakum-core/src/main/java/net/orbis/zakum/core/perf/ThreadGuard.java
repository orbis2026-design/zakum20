package net.orbis.zakum.core.perf;

import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Guardrail to detect blocking I/O on the primary thread.
 * Designed to warn (or fail) when storage/HTTP/Redis calls happen on main.
 */
public final class ThreadGuard {

  private final boolean configuredEnabled;
  private final boolean failOnViolation;
  private final int maxReportsPerMinute;
  private final MetricsMonitor metrics;
  private final Logger logger;

  private final AtomicBoolean runtimeEnabled = new AtomicBoolean(true);
  private final LongAdder violations = new LongAdder();
  private final ConcurrentHashMap<String, LongAdder> byOperation = new ConcurrentHashMap<>();
  private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());
  private final AtomicInteger windowCount = new AtomicInteger(0);

  private volatile String lastViolation;
  private volatile long lastViolationAtMs;

  public ThreadGuard(ZakumSettings.Operations.ThreadGuard cfg, Logger logger, MetricsMonitor metrics) {
    this.configuredEnabled = cfg != null && cfg.enabled();
    this.failOnViolation = cfg != null && cfg.failOnViolation();
    this.maxReportsPerMinute = cfg == null ? 0 : Math.max(0, cfg.maxReportsPerMinute());
    this.logger = Objects.requireNonNull(logger, "logger");
    this.metrics = metrics;
  }

  public boolean checkAsync(String operation) {
    if (!enabled()) return true;
    if (!Bukkit.isPrimaryThread()) return true;
    recordViolation(operation);
    if (failOnViolation) {
      throw new IllegalStateException("Main-thread I/O detected: " + operation);
    }
    return false;
  }

  public boolean enabled() {
    return configuredEnabled && runtimeEnabled.get();
  }

  public boolean configuredEnabled() {
    return configuredEnabled;
  }

  public boolean runtimeEnabled() {
    return runtimeEnabled.get();
  }

  public void setRuntimeEnabled(boolean enabled) {
    runtimeEnabled.set(enabled);
  }

  public Snapshot snapshot() {
    return new Snapshot(
      configuredEnabled,
      runtimeEnabled.get(),
      enabled(),
      failOnViolation,
      maxReportsPerMinute,
      violations.sum(),
      lastViolation,
      lastViolationAtMs,
      topOperations(5)
    );
  }

  private void recordViolation(String operation) {
    String op = operation == null || operation.isBlank() ? "unknown" : operation;
    violations.increment();
    byOperation.computeIfAbsent(op, __ -> new LongAdder()).increment();
    lastViolation = op;
    lastViolationAtMs = System.currentTimeMillis();
    if (metrics != null) metrics.recordAction("thread_guard_violation");
    if (shouldLog()) {
      logger.warning("ThreadGuard: main-thread I/O detected (" + op + "), thread=" +
        Thread.currentThread().getName());
    }
  }

  private boolean shouldLog() {
    int limit = maxReportsPerMinute;
    if (limit <= 0) return false;
    long now = System.currentTimeMillis();
    long start = windowStartMs.get();
    if (now - start > 60_000L) {
      if (windowStartMs.compareAndSet(start, now)) {
        windowCount.set(0);
      }
    }
    return windowCount.incrementAndGet() <= limit;
  }

  private Map<String, Long> topOperations(int limit) {
    if (limit <= 0 || byOperation.isEmpty()) return Map.of();
    return byOperation.entrySet().stream()
      .sorted(Comparator.comparingLong((Map.Entry<String, LongAdder> e) -> e.getValue().sum()).reversed())
      .limit(limit)
      .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue().sum()), Map::putAll);
  }

  public record Snapshot(
    boolean configuredEnabled,
    boolean runtimeEnabled,
    boolean enabled,
    boolean failOnViolation,
    int maxReportsPerMinute,
    long violations,
    String lastViolation,
    long lastViolationAtMs,
    Map<String, Long> topOperations
  ) {}
}
