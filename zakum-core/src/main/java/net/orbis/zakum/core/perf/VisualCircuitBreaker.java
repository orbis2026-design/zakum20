package net.orbis.zakum.core.perf;

import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Global fallback circuit breaker for heavy visuals.
 * Paper does not expose region TPS directly, so we use 1m server TPS as a conservative guard.
 */
public final class VisualCircuitBreaker {

  private final ZakumSettings.Operations.CircuitBreaker cfg;
  private final Logger logger;
  private final MetricsMonitor metrics;

  private int schedulerTaskId = -1;
  private int stableSamples;
  private volatile double lastTps;

  public VisualCircuitBreaker(ZakumSettings.Operations.CircuitBreaker cfg, Logger logger, MetricsMonitor metrics) {
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.metrics = metrics;
    this.lastTps = 20.0d;
    this.stableSamples = 0;
    VisualCircuitState.close();
  }

  public void start(ZakumScheduler scheduler, Plugin owner) {
    Objects.requireNonNull(scheduler, "scheduler");
    Objects.requireNonNull(owner, "owner");

    if (!cfg.enabled()) {
      VisualCircuitState.close();
      return;
    }
    if (schedulerTaskId > 0) return;

    int ticks = Math.max(1, cfg.sampleTicks());
    this.schedulerTaskId = scheduler.runTaskTimer(owner, this::sample, ticks, ticks);
  }

  public void stop(ZakumScheduler scheduler) {
    if (schedulerTaskId > 0 && scheduler != null) {
      scheduler.cancelTask(schedulerTaskId);
    }
    schedulerTaskId = -1;
    stableSamples = 0;
    VisualCircuitState.close();
  }

  public Snapshot snapshot() {
    return new Snapshot(
      cfg.enabled(),
      VisualCircuitState.isOpen(),
      VisualCircuitState.reason(),
      VisualCircuitState.changedAtMs(),
      lastTps,
      schedulerTaskId
    );
  }

  private void sample() {
    lastTps = currentTps();

    if (!VisualCircuitState.isOpen()) {
      stableSamples = 0;
      if (lastTps < cfg.disableBelowTps()) {
        String reason = "tps=" + format(lastTps) + " below " + format(cfg.disableBelowTps());
        VisualCircuitState.open(reason);
        logger.warning("[CircuitBreaker] Visual effects suppressed: " + reason);
        if (metrics != null) metrics.recordAction("visual_circuit_open");
      }
      return;
    }

    if (lastTps >= cfg.resumeAboveTps()) {
      stableSamples++;
      if (stableSamples >= cfg.stableSamplesToClose()) {
        VisualCircuitState.close();
        stableSamples = 0;
        logger.info("[CircuitBreaker] Visual effects restored (tps=" + format(lastTps) + ").");
        if (metrics != null) metrics.recordAction("visual_circuit_close");
      }
      return;
    }

    stableSamples = 0;
  }

  private static double currentTps() {
    try {
      double[] tps = Bukkit.getTPS();
      if (tps == null || tps.length == 0) return 20.0d;
      double oneMinute = tps[0];
      if (Double.isFinite(oneMinute) && oneMinute > 0.0d) {
        return oneMinute;
      }
    } catch (Throwable ignored) {
      // Fallback to healthy default.
    }
    return 20.0d;
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  public record Snapshot(
    boolean enabled,
    boolean open,
    String reason,
    long changedAtMs,
    double lastTps,
    int taskId
  ) {}
}
