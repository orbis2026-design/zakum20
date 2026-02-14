package net.orbis.zakum.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

public final class MetricsMonitor {

  private final MeterRegistry registry;

  public MetricsMonitor(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordAction(String actionType) {
    if (actionType == null || actionType.isBlank()) return;
    registry.counter("zakum_actions_total", "type", actionType).increment();
  }

  public void recordAceExecution(long durationNanos, int resolvedEffects) {
    if (durationNanos < 0L) return;
    Timer timer = registry.timer("zakum_ace_execution_seconds");
    timer.record(durationNanos, TimeUnit.NANOSECONDS);
    registry.summary("zakum_ace_effects_per_script").record(Math.max(0, resolvedEffects));
  }
}
