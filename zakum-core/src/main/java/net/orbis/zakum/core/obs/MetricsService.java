package net.orbis.zakum.core.obs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import net.orbis.zakum.api.config.ZakumSettings;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public final class MetricsService {

  private static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

  private final Logger logger;
  private final ZakumSettings.Observability.Metrics config;
  private final Executor async;
  private final PrometheusMeterRegistry registry;
  private HttpServer server;
  private JvmGcMetrics jvmGc;

  public MetricsService(Logger logger, ZakumSettings.Observability.Metrics config, Executor async) {
    this.logger = logger;
    this.config = config;
    this.async = async;
    this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
  }

  public synchronized void start() {
    if (!config.enabled()) return;
    if (server != null) return;

    if (config.includeJvm()) bindJvmMetrics();

    try {
      InetSocketAddress address = new InetSocketAddress(config.bindHost(), config.port());
      server = HttpServer.create(address, 0);
      server.setExecutor(async);
      server.createContext(normalizePath(config.path()), this::handleScrape);
      server.start();
      logger.info(
        "Prometheus metrics started on http://" + config.bindHost() + ":" + config.port() + normalizePath(config.path())
      );
    } catch (Exception e) {
      logger.warning("Failed to start metrics endpoint: " + e.getMessage());
      if (server != null) {
        server.stop(0);
        server = null;
      }
    }
  }

  public synchronized void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
    if (jvmGc != null) {
      try {
        jvmGc.close();
      } catch (Exception ignored) {
      }
      jvmGc = null;
    }
  }

  public MeterRegistry registry() {
    return registry;
  }

  private void bindJvmMetrics() {
    new ClassLoaderMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    jvmGc = new JvmGcMetrics();
    jvmGc.bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new UptimeMetrics().bindTo(registry);
  }

  private void handleScrape(HttpExchange exchange) throws IOException {
    byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private static String normalizePath(String path) {
    if (path == null || path.isBlank()) return "/metrics";
    return path.startsWith("/") ? path : "/" + path;
  }
}
