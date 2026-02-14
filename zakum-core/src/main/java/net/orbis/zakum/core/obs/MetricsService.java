package net.orbis.zakum.core.obs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import net.orbis.zakum.api.config.ZakumSettings;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class MetricsService {

    private final Logger logger;
    private final ZakumSettings.MetricsConfig config;
    private final Executor async;
    private final PrometheusMeterRegistry registry;

    public MetricsService(Logger logger, ZakumSettings.MetricsConfig config, Executor async) {
        this.logger = logger;
        this.config = config;
        this.async = async;
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    public void start() {
        if (!config.enabled()) return;
    }

    public void stop() {
    }

    public MeterRegistry registry() {
        return registry;
    }
}
