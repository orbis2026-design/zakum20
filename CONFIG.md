# Zakum Configuration Reference

**Version:** 0.1.0-SNAPSHOT  
**Last Updated:** February 18, 2026  
**Module Coverage:** All 27 modules

---

## Overview

This document provides a comprehensive reference for all configuration options across the Zakum plugin ecosystem. Configuration files use YAML format and are validated on startup with strict range checking.

---

## Table of Contents

1. [zakum-core Configuration](#zakum-core-configuration)
2. [zakum-battlepass Configuration](#zakum-battlepass-configuration)
3. [zakum-crates Configuration](#zakum-crates-configuration)
4. [zakum-pets Configuration](#zakum-pets-configuration)
5. [zakum-miniaturepets Configuration](#zakum-miniaturepets-configuration)
6. [zakum-teams Configuration](#zakum-teams-configuration)
7. [orbis-essentials Configuration](#orbis-essentials-configuration)
8. [orbis-gui Configuration](#orbis-gui-configuration)
9. [orbis-hud Configuration](#orbis-hud-configuration)
10. [Bridge Modules Configuration](#bridge-modules-configuration)

---

## zakum-core Configuration

**File Location:** `plugins/Zakum/config.yml`

### Server Settings

```yaml
server:
  id: "server-1"  # Unique server identifier for multi-server setups
```

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `server.id` | String | "unknown" | N/A | Unique identifier for this server instance. Required for cloud features. |

---

### Database Settings

```yaml
database:
  enabled: true
  host: "127.0.0.1"
  port: 3306
  database: "zakum"
  user: "root"
  password: ""
  params: "?useSSL=false&serverTimezone=UTC"
  
  pool:
    maxPoolSize: 10
    minIdle: 2
    connectionTimeoutMs: 5000
    validationTimeoutMs: 3000
    idleTimeoutMs: 600000
    maxLifetimeMs: 1800000
    leakDetectionMs: 0
  
  failover:
    retrySeconds: 30
```

#### Database Connection

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `database.enabled` | Boolean | true | N/A | Enable/disable database connectivity |
| `database.host` | String | "127.0.0.1" | N/A | MySQL/MariaDB host address |
| `database.port` | Integer | 3306 | 1-65535 | Database port |
| `database.database` | String | "zakum" | N/A | Database name |
| `database.user` | String | "root" | N/A | Database username |
| `database.password` | String | "" | N/A | Database password |
| `database.params` | String | "" | N/A | Additional JDBC parameters |

#### Connection Pool

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `database.pool.maxPoolSize` | Integer | 10 | 1-100 | Maximum connections in pool |
| `database.pool.minIdle` | Integer | 2 | 0-maxPoolSize | Minimum idle connections |
| `database.pool.connectionTimeoutMs` | Long | 5000 | 250-60000 | Connection acquisition timeout (ms) |
| `database.pool.validationTimeoutMs` | Long | 3000 | 250-10000 | Connection validation timeout (ms) |
| `database.pool.idleTimeoutMs` | Long | 600000 | 10000-1800000 | Idle connection timeout (ms) |
| `database.pool.maxLifetimeMs` | Long | 1800000 | 30000-3600000 | Maximum connection lifetime (ms) |
| `database.pool.leakDetectionMs` | Long | 0 | 0-60000 | Leak detection threshold (0=disabled) |

#### Failover

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `database.failover.retrySeconds` | Long | 30 | 5-600 | Reconnection attempt interval |

---

### Control Plane Settings

```yaml
controlPlane:
  enabled: false
  baseUrl: "https://api.example.com"
  apiKey: ""
```

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `controlPlane.enabled` | Boolean | false | N/A | Enable cloud control plane integration |
| `controlPlane.baseUrl` | String | "" | N/A | Control plane API base URL |
| `controlPlane.apiKey` | String | "" | N/A | API authentication key |

---

### Cache Settings

```yaml
cache:
  defaults:
    maximumSize: 10000
    expireAfterWriteSeconds: 600
    expireAfterAccessSeconds: 300
  
  burst:
    enabled: false
    redisUri: "redis://localhost:6379"
    keyPrefix: "zakum:"
    defaultTtlSeconds: 300
    maximumLocalEntries: 1000
```

#### Default Cache Settings

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `cache.defaults.maximumSize` | Long | 10000 | 100-1000000 | Maximum cache entries |
| `cache.defaults.expireAfterWriteSeconds` | Long | 600 | 10-86400 | TTL after write (seconds) |
| `cache.defaults.expireAfterAccessSeconds` | Long | 300 | 10-86400 | TTL after access (seconds) |

#### Burst Cache (Redis)

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `cache.burst.enabled` | Boolean | false | N/A | Enable Redis burst cache |
| `cache.burst.redisUri` | String | "" | N/A | Redis connection URI |
| `cache.burst.keyPrefix` | String | "zakum:" | N/A | Redis key prefix |
| `cache.burst.defaultTtlSeconds` | Long | 300 | 10-86400 | Default TTL for cached entries |
| `cache.burst.maximumLocalEntries` | Long | 1000 | 100-100000 | Local cache size before Redis |

---

### Entitlements Settings

```yaml
entitlements:
  cache:
    maximumSize: 10000
    ttlSeconds: 600
```

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `entitlements.cache.maximumSize` | Long | 10000 | 100-1000000 | Maximum cached entitlement checks |
| `entitlements.cache.ttlSeconds` | Long | 600 | 60-86400 | Cache TTL (seconds) |

---

### Boosters Settings

```yaml
boosters:
  refreshSeconds: 60
  purge:
    enabled: true
    intervalSeconds: 600
    deleteLimit: 5000
```

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `boosters.refreshSeconds` | Integer | 60 | 10-3600 | Booster refresh interval |
| `boosters.purge.enabled` | Boolean | true | N/A | Enable expired booster purging |
| `boosters.purge.intervalSeconds` | Integer | 600 | 60-86400 | Purge check interval |
| `boosters.purge.deleteLimit` | Integer | 5000 | 100-50000 | Max records per purge batch |

---

### Actions Settings

```yaml
actions:
  enabled: true
  emitters:
    joinQuit: true
    onlineTime: true
    blockBreak: true
    blockPlace: true
    mobKill: true
    playerDeath: true
    playerKill: true
    xpGain: true
    levelChange: true
    itemCraft: true
    smeltExtract: true
    fishCatch: true
    itemEnchant: true
    itemConsume: true
    advancement: true
    commandUse:
      enabled: false
      allowlist: []
  
  movement:
    enabled: false
    sampleTicks: 20
    maxCmPerSample: 1000
  
  deferredReplay:
    enabled: true
    claimLimit: 10000
```

#### Action Emitters

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `actions.enabled` | Boolean | true | Master switch for action system |
| `actions.emitters.joinQuit` | Boolean | true | Emit join/quit events |
| `actions.emitters.onlineTime` | Boolean | true | Emit online time tracking |
| `actions.emitters.blockBreak` | Boolean | true | Emit block break events |
| `actions.emitters.blockPlace` | Boolean | true | Emit block place events |
| `actions.emitters.mobKill` | Boolean | true | Emit mob kill events |
| `actions.emitters.playerDeath` | Boolean | true | Emit player death events |
| `actions.emitters.playerKill` | Boolean | true | Emit player kill events |
| `actions.emitters.xpGain` | Boolean | true | Emit XP gain events |
| `actions.emitters.levelChange` | Boolean | true | Emit level change events |
| `actions.emitters.itemCraft` | Boolean | true | Emit crafting events |
| `actions.emitters.smeltExtract` | Boolean | true | Emit smelting events |
| `actions.emitters.fishCatch` | Boolean | true | Emit fishing events |
| `actions.emitters.itemEnchant` | Boolean | true | Emit enchanting events |
| `actions.emitters.itemConsume` | Boolean | true | Emit item consumption events |
| `actions.emitters.advancement` | Boolean | true | Emit advancement events |
| `actions.emitters.commandUse.enabled` | Boolean | false | Track command usage |
| `actions.emitters.commandUse.allowlist` | List<String> | [] | Commands to track |

#### Movement Tracking

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `actions.movement.enabled` | Boolean | false | N/A | Enable movement tracking |
| `actions.movement.sampleTicks` | Integer | 20 | 1-100 | Sample interval (ticks) |
| `actions.movement.maxCmPerSample` | Long | 1000 | 10-100000 | Max movement per sample (cm) |

#### Deferred Replay

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `actions.deferredReplay.enabled` | Boolean | true | N/A | Enable offline action replay |
| `actions.deferredReplay.claimLimit` | Integer | 10000 | 100-1000000 | Max actions to replay per claim |

---

### HTTP Settings

```yaml
http:
  connectTimeoutMs: 5000
  callTimeoutMs: 30000
  readTimeoutMs: 30000
  writeTimeoutMs: 30000
  maxRequests: 64
  maxRequestsPerHost: 5
  
  resilience:
    enabled: true
    circuitBreaker:
      failureRateThreshold: 50.0
      slowCallRateThreshold: 100.0
      slowCallDurationMs: 5000
      slidingWindowSize: 100
      minimumNumberOfCalls: 10
      waitDurationInOpenStateMs: 60000
    retry:
      maxAttempts: 3
      waitDurationMs: 500
```

#### HTTP Client

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `http.connectTimeoutMs` | Long | 5000 | 100-60000 | Connection timeout (ms) |
| `http.callTimeoutMs` | Long | 30000 | 1000-300000 | Call timeout (ms) |
| `http.readTimeoutMs` | Long | 30000 | 1000-300000 | Read timeout (ms) |
| `http.writeTimeoutMs` | Long | 30000 | 1000-300000 | Write timeout (ms) |
| `http.maxRequests` | Integer | 64 | 8-256 | Maximum concurrent requests |
| `http.maxRequestsPerHost` | Integer | 5 | 1-64 | Max requests per host |

#### Resilience

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `http.resilience.enabled` | Boolean | true | N/A | Enable circuit breaker + retry |
| `http.resilience.circuitBreaker.failureRateThreshold` | Float | 50.0 | 1.0-100.0 | Failure rate to open circuit (%) |
| `http.resilience.circuitBreaker.slowCallRateThreshold` | Float | 100.0 | 1.0-100.0 | Slow call rate to open circuit (%) |
| `http.resilience.circuitBreaker.slowCallDurationMs` | Long | 5000 | 100-60000 | Threshold for slow calls (ms) |
| `http.resilience.circuitBreaker.slidingWindowSize` | Integer | 100 | 10-1000 | Sliding window size |
| `http.resilience.circuitBreaker.minimumNumberOfCalls` | Integer | 10 | 1-100 | Min calls before evaluation |
| `http.resilience.circuitBreaker.waitDurationInOpenStateMs` | Long | 60000 | 1000-600000 | Wait before retry (ms) |
| `http.resilience.retry.maxAttempts` | Integer | 3 | 1-10 | Maximum retry attempts |
| `http.resilience.retry.waitDurationMs` | Long | 500 | 100-10000 | Wait between retries (ms) |

---

### Observability Settings

```yaml
observability:
  metrics:
    enabled: false
    bindHost: "0.0.0.0"
    port: 9090
    path: "/metrics"
    includeJvm: true
```

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `observability.metrics.enabled` | Boolean | false | N/A | Enable Prometheus metrics |
| `observability.metrics.bindHost` | String | "0.0.0.0" | N/A | Metrics endpoint bind address |
| `observability.metrics.port` | Integer | 9090 | 1024-65535 | Metrics endpoint port |
| `observability.metrics.path` | String | "/metrics" | N/A | Metrics endpoint path |
| `observability.metrics.includeJvm` | Boolean | true | N/A | Include JVM metrics |

---

## Configuration Validation

All configuration values are validated on startup with the following rules:

### Range Clamping

Out-of-range values are automatically clamped to safe limits:
- **Numeric ranges:** Values outside min/max are adjusted
- **String values:** Blank strings trigger defaults
- **Boolean values:** Invalid values default to false

### Validation Errors

Invalid configurations will:
1. Log a clear error message
2. Use safe default value
3. Continue startup (fail-safe design)

### Best Practices

1. **Start with defaults** - Default configuration is production-ready
2. **Tune incrementally** - Change one setting at a time
3. **Monitor metrics** - Enable observability for production
4. **Test changes** - Validate in staging before production
5. **Document changes** - Keep notes on non-default values

---

## Next Sections

- [zakum-battlepass Configuration](#zakum-battlepass-configuration)
- [Bridge Modules Configuration](#bridge-modules-configuration)
- [Commands Reference](COMMANDS.md)

---

**Last Updated:** February 18, 2026  
**Module Version:** 0.1.0-SNAPSHOT

