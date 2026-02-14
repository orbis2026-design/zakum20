# Central Configuration

Config file: `plugins/Zakum/config.yml`

Zakum loads a **typed snapshot** into:
- `net.orbis.zakum.api.config.ZakumSettings`

This snapshot is:
- immutable
- validated (ranges clamped)
- safe to share with other plugins

## Categories

### server
- `server.id`:
  - required identity for per-server scoping (battlepass progress, warps, etc.)

### database
- all JDBC/Hikari settings
- failover retry seconds

### controlPlane
- optional Orbis Cloud Bot integration endpoint + API key

### entitlements
- cache size and TTL

### actions
- emitter toggles (join/quit, block break, mob kill, etc.)
- movement sampler cadence and max jump threshold
- deferred replay (claim limit, enable/disable)

### packets
- `packets.enabled`: master toggle for packet layer
- `packets.backend`: `NONE` | `PACKETEVENTS`
- `packets.inbound` / `packets.outbound`: direction toggles
- `packets.maxHooksPerPlugin`: guardrail to prevent hook spam

### http
- shared defaults for any outbound HTTP client
- optional resilience policies (circuit breaker + retry)

### cache
- default cache posture for future systems

### observability
- `observability.metrics.enabled`:
  - starts Prometheus endpoint on `bindHost:port/path`

## Loader details
Loader: `zakum-core/.../ZakumSettingsLoader.java`
- clamps ranges
- normalizes allowlists
- avoids nulls and blank config hazards
