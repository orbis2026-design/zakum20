package net.orbis.zakum.core.obs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import net.orbis.zakum.api.config.ZakumSettings;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Lightweight Prometheus endpoint for long-uptime observability.
 *
 * Disabled by default (observability.metrics.enabled=false).
 *
 * IMPORTANT:
 * - Binds only to the configured host/port (default 127.0.0.1).
 * - No auth in v1; keep it on localhost or behind firewall/VPN.
 */
public final class MetricsService {

  private final Logger log;
  private final ZakumSettings.Observability.Metrics cfg;
  private final Executor async;

  private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

  private HttpServer server;

  public MetricsService(Logger log, ZakumSettings.Observability.Metrics cfg, Executor async) {
    this.log = Objects.requireNonNull(log, "log");
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.async = Objects.requireNonNull(async, "async");
  }

  public MeterRegistry registry() {
    return registry;
  }

  public void start() {
    if (!cfg.enabled()) return;

    if (cfg.includeJvm()) {
      new JvmMemoryMetrics().bindTo(registry);
      new JvmGcMetrics().bindTo(registry);
      new JvmThreadMetrics().bindTo(registry);
      new ProcessorMetrics().bindTo(registry);
      new UptimeMetrics().bindTo(registry);
    }

    try {
      String path = cfg.path();
      if (path == null || path.isBlank()) path = "/metrics";
      if (!path.startsWith("/")) path = "/" + path;

      server = HttpServer.create(new InetSocketAddress(cfg.bindHost(), cfg.port()), 0);
      server.createContext(path, ex -> {
        byte[] out = registry.scrape().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        ex.sendResponseHeaders(200, out.length);
        try (OutputStream os = ex.getResponseBody()) {
          os.write(out);
        }
      });

      server.setExecutor(r -> async.execute(r));
      server.start();

      log.info("Metrics endpoint enabled: http://" + cfg.bindHost() + ":" + cfg.port() + path);
    } catch (Throwable t) {
      log.warning("Failed to start metrics endpoint: " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  public void stop() {
    if (server != null) {
      try { server.stop(0); } catch (Throwable ignored) {}
      server = null;
    }
  }
}
