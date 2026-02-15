package net.orbis.zakum.core.ops;

import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.core.concurrent.ZakumSchedulerImpl;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import net.orbis.zakum.core.perf.ThreadGuard;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Long-run soak automation profile with telemetry assertions.
 */
public final class SoakAutomationProfile implements AutoCloseable {

  private static final int TICKS_PER_SECOND = 20;

  private final Plugin plugin;
  private final ZakumScheduler scheduler;
  private final ZakumSchedulerImpl schedulerRuntime;
  private final ThreadGuard threadGuard;
  private final StressHarnessV2 stressHarness;
  private final ZakumSettings.Operations.Soak cfg;
  private final MetricsMonitor metrics;
  private final Logger logger;
  private final AtomicLong runCounter = new AtomicLong();

  private volatile boolean running;
  private volatile int taskId;
  private volatile long runId;
  private volatile long startedAtMs;
  private volatile long stopAtMs;
  private volatile long lastSampleAtMs;
  private volatile int sampleCount;
  private volatile int lowTpsConsecutive;
  private volatile int assertionFailures;
  private volatile boolean autoStressStarted;
  private volatile String lastStopReason;
  private volatile String lastAssertion;
  private volatile long baselineThreadGuardViolations;
  private volatile long baselineAsyncRejected;
  private volatile long baselineStressErrors;
  private volatile long deltaThreadGuardViolations;
  private volatile long deltaAsyncRejected;
  private volatile long deltaStressErrors;
  private volatile double lastTps;
  private volatile int durationMinutes;

  public SoakAutomationProfile(
    Plugin plugin,
    ZakumScheduler scheduler,
    ZakumSchedulerImpl schedulerRuntime,
    ThreadGuard threadGuard,
    StressHarnessV2 stressHarness,
    ZakumSettings.Operations.Soak cfg,
    MetricsMonitor metrics,
    Logger logger
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.schedulerRuntime = Objects.requireNonNull(schedulerRuntime, "schedulerRuntime");
    this.threadGuard = Objects.requireNonNull(threadGuard, "threadGuard");
    this.stressHarness = Objects.requireNonNull(stressHarness, "stressHarness");
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.metrics = metrics;
    this.logger = Objects.requireNonNull(logger, "logger");
    this.running = false;
    this.taskId = -1;
    this.runId = 0L;
    this.startedAtMs = 0L;
    this.stopAtMs = 0L;
    this.lastSampleAtMs = 0L;
    this.sampleCount = 0;
    this.lowTpsConsecutive = 0;
    this.assertionFailures = 0;
    this.autoStressStarted = false;
    this.lastStopReason = "never";
    this.lastAssertion = "";
    this.baselineThreadGuardViolations = 0L;
    this.baselineAsyncRejected = 0L;
    this.baselineStressErrors = 0L;
    this.deltaThreadGuardViolations = 0L;
    this.deltaAsyncRejected = 0L;
    this.deltaStressErrors = 0L;
    this.lastTps = 20.0d;
    this.durationMinutes = cfg.durationMinutes();
  }

  public StartResult start(Integer durationOverrideMinutes) {
    if (!cfg.enabled()) {
      return new StartResult(false, "Soak profile is disabled in config.");
    }
    if (running) {
      return new StartResult(false, "Soak profile already running.");
    }

    int resolvedDuration = durationOverrideMinutes == null
      ? cfg.durationMinutes()
      : Math.max(1, Math.min(durationOverrideMinutes, 24 * 60));
    if (resolvedDuration <= 0) {
      resolvedDuration = 1;
    }

    long now = System.currentTimeMillis();
    this.runId = runCounter.incrementAndGet();
    this.durationMinutes = resolvedDuration;
    this.startedAtMs = now;
    this.stopAtMs = now + Duration.ofMinutes(resolvedDuration).toMillis();
    this.lastSampleAtMs = 0L;
    this.sampleCount = 0;
    this.lowTpsConsecutive = 0;
    this.assertionFailures = 0;
    this.lastAssertion = "";
    this.lastTps = currentTps();
    this.autoStressStarted = false;

    this.baselineThreadGuardViolations = threadGuard.snapshot().violations();
    this.baselineAsyncRejected = schedulerRuntime.asyncSnapshot().rejected();
    this.baselineStressErrors = stressHarness.snapshot().errorCount();
    this.deltaThreadGuardViolations = 0L;
    this.deltaAsyncRejected = 0L;
    this.deltaStressErrors = 0L;

    if (cfg.autoStartStress() && !stressHarness.snapshot().running()) {
      int iterations = cfg.stressIterations() <= 0 ? 0 : cfg.stressIterations();
      Integer virtualPlayers = cfg.stressVirtualPlayers() <= 0 ? null : cfg.stressVirtualPlayers();
      StressHarnessV2.StartResult startResult = stressHarness.start(iterations, virtualPlayers);
      this.autoStressStarted = startResult.started();
      if (!startResult.started()) {
        logger.warning("Soak profile could not auto-start stress harness: " + startResult.message());
      }
    }

    running = true;
    int intervalTicks = Math.max(1, cfg.sampleIntervalSeconds()) * TICKS_PER_SECOND;
    if (taskId > 0) {
      scheduler.cancelTask(taskId);
    }
    taskId = scheduler.runTaskTimer(plugin, this::tick, 1L, intervalTicks);
    recordAction("soak_profile_start");
    logger.info(
      "Soak profile started runId=" + runId
        + " durationMinutes=" + resolvedDuration
        + " sampleIntervalSeconds=" + cfg.sampleIntervalSeconds()
        + " autoStressStarted=" + autoStressStarted
    );
    return new StartResult(true, "Soak profile started (duration=" + resolvedDuration + "m).");
  }

  public StopResult stop(String reason) {
    if (!running) {
      return new StopResult(false, "Soak profile is not running.");
    }
    String finalReason = (reason == null || reason.isBlank()) ? "manual" : reason.trim();
    stopInternal(finalReason, false);
    return new StopResult(true, "Soak profile stopped (" + finalReason + ").");
  }

  public Snapshot snapshot() {
    return new Snapshot(
      cfg.enabled(),
      running,
      runId,
      taskId,
      startedAtMs,
      stopAtMs,
      lastSampleAtMs,
      durationMinutes,
      cfg.sampleIntervalSeconds(),
      cfg.minTps(),
      cfg.maxConsecutiveLowTpsSamples(),
      cfg.maxThreadGuardViolationDelta(),
      cfg.maxAsyncRejectedDelta(),
      cfg.maxStressErrorDelta(),
      cfg.abortOnAssertionFailure(),
      autoStressStarted,
      sampleCount,
      lowTpsConsecutive,
      assertionFailures,
      baselineThreadGuardViolations,
      baselineAsyncRejected,
      baselineStressErrors,
      deltaThreadGuardViolations,
      deltaAsyncRejected,
      deltaStressErrors,
      lastTps,
      lastAssertion,
      lastStopReason
    );
  }

  @Override
  public void close() {
    if (!running) return;
    stopInternal("shutdown", true);
  }

  private void tick() {
    if (!running) return;
    long now = System.currentTimeMillis();
    if (now >= stopAtMs) {
      stopInternal("completed", false);
      return;
    }
    sample(now);
  }

  private void sample(long now) {
    this.lastSampleAtMs = now;
    this.sampleCount++;
    this.lastTps = currentTps();

    long currentThreadGuardViolations = threadGuard.snapshot().violations();
    long currentAsyncRejected = schedulerRuntime.asyncSnapshot().rejected();
    long currentStressErrors = stressHarness.snapshot().errorCount();

    this.deltaThreadGuardViolations = Math.max(0L, currentThreadGuardViolations - baselineThreadGuardViolations);
    this.deltaAsyncRejected = Math.max(0L, currentAsyncRejected - baselineAsyncRejected);
    this.deltaStressErrors = Math.max(0L, currentStressErrors - baselineStressErrors);

    if (lastTps < cfg.minTps()) {
      lowTpsConsecutive++;
    } else {
      lowTpsConsecutive = 0;
    }

    String assertion = evaluateAssertion();
    if (assertion != null && !assertion.isBlank()) {
      assertionFailures++;
      lastAssertion = assertion;
      recordAction("soak_profile_assertion_failure");
      logger.warning("Soak assertion failed: " + assertion);
      if (cfg.abortOnAssertionFailure()) {
        stopInternal("assertion_failure", false);
        return;
      }
    }

    recordAction("soak_profile_sample");
  }

  private String evaluateAssertion() {
    if (cfg.maxConsecutiveLowTpsSamples() > 0 && lowTpsConsecutive >= cfg.maxConsecutiveLowTpsSamples()) {
      return "low_tps_consecutive=" + lowTpsConsecutive + " threshold=" + cfg.maxConsecutiveLowTpsSamples();
    }
    if (cfg.maxThreadGuardViolationDelta() >= 0 && deltaThreadGuardViolations > cfg.maxThreadGuardViolationDelta()) {
      return "threadguard_delta=" + deltaThreadGuardViolations + " threshold=" + cfg.maxThreadGuardViolationDelta();
    }
    if (cfg.maxAsyncRejectedDelta() >= 0 && deltaAsyncRejected > cfg.maxAsyncRejectedDelta()) {
      return "async_rejected_delta=" + deltaAsyncRejected + " threshold=" + cfg.maxAsyncRejectedDelta();
    }
    if (cfg.maxStressErrorDelta() >= 0 && deltaStressErrors > cfg.maxStressErrorDelta()) {
      return "stress_error_delta=" + deltaStressErrors + " threshold=" + cfg.maxStressErrorDelta();
    }
    return "";
  }

  private void stopInternal(String reason, boolean shuttingDown) {
    if (!running && !shuttingDown) return;
    running = false;
    if (taskId > 0) {
      scheduler.cancelTask(taskId);
      taskId = -1;
    }
    lastStopReason = reason == null || reason.isBlank() ? "stopped" : reason.trim();
    recordAction("soak_profile_stop");

    if (!shuttingDown && autoStressStarted && stressHarness.snapshot().running()) {
      stressHarness.stop("soak_profile");
    }

    if (!shuttingDown && cfg.autoWriteStressReport()) {
      String label = buildReportLabel(lastStopReason);
      scheduler.runAsync(() -> stressHarness.writeReport(label));
    }

    logger.info(
      "Soak profile stopped runId=" + runId
        + " reason=" + lastStopReason
        + " samples=" + sampleCount
        + " assertions=" + assertionFailures
        + " tps=" + String.format(java.util.Locale.ROOT, "%.2f", lastTps)
    );
  }

  private String buildReportLabel(String reason) {
    String prefix = cfg.reportLabelPrefix();
    if (prefix == null || prefix.isBlank()) {
      prefix = "soak";
    }
    String safeReason = (reason == null || reason.isBlank()) ? "stop" : reason.trim().toLowerCase(java.util.Locale.ROOT);
    return prefix + "_" + safeReason + "_" + runId;
  }

  private static double currentTps() {
    try {
      double[] tps = Bukkit.getTPS();
      if (tps != null && tps.length > 0) {
        double oneMinute = tps[0];
        if (Double.isFinite(oneMinute) && oneMinute > 0.0d) return oneMinute;
      }
    } catch (Throwable ignored) {
      // ignore
    }
    return 20.0d;
  }

  private void recordAction(String type) {
    if (metrics == null || type == null || type.isBlank()) return;
    metrics.recordAction(type);
  }

  public record StartResult(boolean started, String message) {}

  public record StopResult(boolean stopped, String message) {}

  public record Snapshot(
    boolean configuredEnabled,
    boolean running,
    long runId,
    int taskId,
    long startedAtMs,
    long stopAtMs,
    long lastSampleAtMs,
    int durationMinutes,
    int sampleIntervalSeconds,
    double minTps,
    int maxConsecutiveLowTpsSamples,
    long maxThreadGuardViolationDelta,
    long maxAsyncRejectedDelta,
    long maxStressErrorDelta,
    boolean abortOnAssertionFailure,
    boolean autoStressStarted,
    int sampleCount,
    int lowTpsConsecutive,
    int assertionFailures,
    long baselineThreadGuardViolations,
    long baselineAsyncRejected,
    long baselineStressErrors,
    long deltaThreadGuardViolations,
    long deltaAsyncRejected,
    long deltaStressErrors,
    double lastTps,
    String lastAssertion,
    String lastStopReason
  ) {}
}
