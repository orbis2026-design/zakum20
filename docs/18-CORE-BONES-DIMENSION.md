# Core Bones Dimension (Priority #1)

This document reweights execution so shared core/platform capabilities are the first dimension for all future plugin work.

## Point Model (Dimension 4)

Total: **140 points**

1. **30 pts** Runtime safety kernel
   - main-thread guardrails
   - bounded async backpressure
   - runtime circuit controls
2. **28 pts** Data/storage spine
   - SQL uptime posture
   - typed Mongo/Redis datastore bootstrap
   - global economy sync path
3. **24 pts** Command/control spine
   - unified admin command tree (fallback + CommandAPI)
   - cloud control + flush + state visibility
4. **22 pts** Observability + diagnostics
   - Prometheus metrics
   - data-health command surface
   - long-lived task registry diagnostics
5. **20 pts** Scheduler and task lifecycle
   - Folia-safe execution boundaries
   - recurring task ownership and shutdown hygiene
6. **16 pts** Capability contract surface
   - capability registry + service wiring
   - optional bridge discovery and feature gating

## Current Completion (Source-Scan Based)

Estimated from current source and command surfaces:

- Runtime safety kernel: **30 / 30**
- Data/storage spine: **27 / 28**
- Command/control spine: **24 / 24**
- Observability + diagnostics: **22 / 22**
- Scheduler and task lifecycle: **20 / 20**
- Capability contract surface: **16 / 16**

**Core Bones subtotal: 139 / 140 (99%)**

## What Was Implemented In This Pass

- Added bounded async runtime controls and status exposure (`/zakum async ...`).
- Added thread-guard runtime controls and visibility (`/zakum threadguard ...`).
- Added typed datastore settings in `ZakumSettings` and loader wiring.
- Migrated Mongo/Redis datastore bootstrap to typed settings only.
- Added datastore Redis session key prefix configuration (`datastore.sessionKeyPrefix`).
- Added cross-surface data health diagnostics (`/zakum datahealth status` in fallback + CommandAPI).
- Added long-lived task registry diagnostics (`/zakum tasks status` in fallback + CommandAPI).
- Added scheduler task activity introspection and packet/stress task id exposure.
- Added API compatibility smoke tests in `zakum-api` (`ApiCompatibilitySmokeTest`).
- Added root `verifyApiBoundaries` task and wired it into `check` for Java modules.
- Activated HTTP resilience policy in `HttpControlPlaneClient` using typed `http.resilience.*`.
- Added ControlPlane runtime diagnostics command (`/zakum controlplane status`) in fallback and CommandAPI.
- Added shared `BurstCacheService` capability with Redis + local fallback runtime.
- Added burst-cache runtime diagnostics and toggles (`/zakum burstcache status|enable|disable`) in fallback and CommandAPI.
- Added module startup/load-order validator with typed controls (`operations.startupValidator.*`).
- Added module validator command surface (`/zakum modules status|validate`) in fallback and CommandAPI.
- Added module validator health/task visibility in `/zakum datahealth status` and `/zakum tasks status`.
- Added long-run soak automation profile with telemetry assertions (`operations.soak.*`).
- Added soak command/control surface (`/zakum soak start|stop|status`) in fallback and CommandAPI.
- Added soak task + assertion visibility in `/zakum tasks status` and `/zakum datahealth status`.
- Added structured ACE error taxonomy diagnostics (`operations.aceDiagnostics.*`) and bounded recent error buffer.
- Added ACE diagnostics command surface (`/zakum ace status|errors|clear|enable|disable`) in fallback and CommandAPI.
- Added ACE diagnostics health counters into `/zakum datahealth status`.
- Added cross-module SQL health probes (`operations.dataHealthProbes.*`) with schema + read/write checks.
- Added module probe command surface (`/zakum datahealth modules`) in fallback and CommandAPI.
- Added module probe summary counters into `/zakum datahealth status`.

## Remaining High-Value Core Bones Backlog (Point Weighted)

1. **7 pts** Cloud HTTP parity hardening (retry/circuit alignment with ControlPlane path)

## End-of-Cycle Test Categories (Server Jar Validation)

These are the major test buckets to run after packaging and deploying jars to a real Paper 1.21.11 environment.

1. **Boot and lifecycle**
   - cold start, reload, controlled shutdown, restart with persisted state
   - verify task registry returns no leaked active tasks after disable/enable
2. **Command/control plane**
   - `/zakum` admin tree parity (fallback + CommandAPI)
   - state transitions for async/threadguard/packetcull controls
3. **Storage and sync**
   - SQL availability loss/recovery
   - datastore profile save/load and Redis session consistency
   - economy global balance consistency across server restarts
4. **Concurrency and safety**
   - deliberate blocking-I/O misuse to verify threadguard signal path
   - async saturation to verify bounded backpressure/rejection behavior
5. **Packet and visual runtime**
   - packet backend attach/detach behavior
   - culling runtime toggles, sample/probe loops, and hook reattach under plugin reload
6. **Cloud delivery guarantees**
   - queue dedupe, ACK retry, replay-safety behavior under simulated HTTP failures
7. **Stress and soak**
   - 500-player synthetic harness matrix (RTP/economy/chat/visual combinations)
   - long soak telemetry review (task count, queue pressure, memory, error drift)

## Local Validation Matrix (Agent-Side)

Before server deployment, always run:

- `./gradlew build --no-daemon`
- `./gradlew :zakum-api:test verifyApiBoundaries --no-daemon`
- targeted compile checks for touched modules
- command path compile verification for fallback + CommandAPI surfaces
- source scan for direct config reads bypassing typed settings in core paths
