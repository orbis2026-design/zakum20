# Observability (Metrics)

Zakum can expose Prometheus metrics.

Config:
```yml
observability:
  metrics:
    enabled: false
    bindHost: "127.0.0.1"
    port: 9100
    path: "/metrics"
    includeJvm: true
```

Implementation:
- `zakum-core/.../obs/MetricsService.java`
- uses Micrometer + Prometheus registry
- binds JVM metrics if enabled

Security note:
- v1 endpoint is unauthenticated
- keep it on localhost or behind firewall/reverse proxy with auth
