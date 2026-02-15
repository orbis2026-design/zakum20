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

### cloud
- `cloud.enabled`: toggles cloud queue polling
- `cloud.dedupe.*`: replay safety and optional persistence
- `cloud.ack.*`: ACK batching and retry policy

### entitlements
- cache size and TTL

### actions
- emitter toggles (join/quit, block break, mob kill, etc.)
- movement sampler cadence and max jump threshold
- deferred replay (claim limit, enable/disable)

### operations
- `operations.circuitBreaker.*`: TPS-based visual circuit breaker
- `operations.threadGuard.*`: detect blocking I/O on the main thread
- `operations.async.*`: bounded async backpressure (`maxInFlight`, `maxQueue`, caller-runs policy)
- `operations.startupValidator.*`: module load-order and startup compatibility guard (`status|validate` command surface)
- `operations.stress.*`: stress harness safety gates + scenario matrix
- `operations.soak.*`: 12h soak automation profile (telemetry assertions, auto stress bootstrap, report labeling)
- `operations.aceDiagnostics.*`: ACE parse/execute error taxonomy diagnostics buffer and limits
- `operations.dataHealthProbes.*`: module SQL schema/read/write probe controls

### datastore
- `datastore.enabled`: toggles Mongo/Redis profile + session capability bootstrap
- `datastore.mongoUri` / `datastore.mongoDatabase`: profile store connection + database
- `datastore.redisUri`: ephemeral/session sync backend
- `datastore.sessionKeyPrefix`: namespaced Redis key prefix for cross-plugin session keys

### economy
- `economy.global.*`: network economy capability controls
- `economy.global.redisUri` falls back to `datastore.redisUri` when blank

### packets
- `packets.enabled`: master toggle for packet layer
- `packets.backend`: `NONE` | `PACKETEVENTS`
- `packets.inbound` / `packets.outbound`: direction toggles
- `packets.maxHooksPerPlugin`: guardrail to prevent hook spam
- `packets.culling.*`: packet-level density culling
  - `sampleTicks`, `radius`, `densityThreshold`, `maxSampleAgeMs`
  - `packetNames` allowlist and `bypassPermission`
  - `respectPerfMode` and `probeIntervalTicks` (reattach safety)

### http
- shared defaults for any outbound HTTP client
- optional resilience policies (circuit breaker + retry)
- enforced by `HttpControlPlaneClient` and cloud delivery (`SecureCloudClient`)

### cache
- default cache posture for future systems
- `cache.burst.*`: shared Redis burst-cache capability (`enabled`, `redisUri`, `keyPrefix`, TTL, local fallback size)

### observability
- `observability.metrics.enabled`:
  - starts Prometheus endpoint on `bindHost:port/path`

## Loader details
Loader: `zakum-core/.../ZakumSettingsLoader.java`
- clamps ranges
- normalizes allowlists
- avoids nulls and blank config hazards


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*
