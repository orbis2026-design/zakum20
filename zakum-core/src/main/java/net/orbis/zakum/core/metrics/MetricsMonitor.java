package net.orbis.zakum.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;

public final class MetricsMonitor {

  private final MeterRegistry registry;

  public MetricsMonitor(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordAction(String actionType) {
    if (actionType == null || actionType.isBlank()) return;
    registry.counter("zakum_actions_total", "type", actionType).increment();
  }
}
