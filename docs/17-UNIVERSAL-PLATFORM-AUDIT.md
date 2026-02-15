# Zakum Universal Platform Audit (2026-02-14)

This is the current, compile-verified state of Zakum after the latest refactor pass.

## 1) System Aspects (What Exists Now)

- API/SPI contracts (`zakum-api`): stable service interfaces for scheduler, ACE, storage, progression, animation, bridges, GUI bridge, capability registry.
- Core runtime (`zakum-core`): service wiring, async executor model, SQL services, ACE runtime, packet animation adapter, capability/service registration.
- GUI platform boundary: `GuiBridge` in API/core + optional runtime implementation in `orbis-gui`.
- Feature products: battlepass, crates, pets, miniaturepets consume API/core services.
- Bridge web: Vault, PlaceholderAPI, LuckPerms, CommandAPI, EssentialsX, Jobs, MythicMobs, Citizens, SuperiorSkyblock2, Votifier.
- Data layers:
  - SQL path: Hikari + Flyway + JDBC abstractions (active/default).
  - NoSQL path: new `DataStore` contract + `MongoDataStore` implementation scaffold (Mongo profiles + Redis session keys).
- Observability: Micrometer + Prometheus integration and metrics runtime in core.
- Packet layer: PacketEvents-backed module and animation support.

## 2) GUI Position in Architecture

Decision: GUI is a **platform service**, not a hard core monolith.

- Keep GUI contracts in `zakum-api`.
- Keep GUI adapter boundary in `zakum-core`.
- Keep concrete renderer/runtime in `orbis-gui`.

This preserves decoupling and lets all feature plugins depend on one stable interface while UI implementation evolves independently.

## 3) Realized Capability Score (Current, End-to-End Build Truth)

Scoring model: 300 total points.

- Build & dependency stability: **29/30**
- API contract maturity: **27/35**
- Scheduler/Folia safety: **25/30**
- Storage/reliability services: **20/35**
- ACE engine + shared effects: **22/40**
- Packet/animation layer: **16/30**
- GUI platform readiness: **16/25**
- Feature plugin parity (crates/pets/battlepass): **15/45**
- Observability/operability: **11/20**
- Test + soak readiness: **4/10**

**Total: 185 / 300**

Rationale: full multi-module `clean build` is passing, platform boundaries exist, and core runtime is operational; parity depth and soak/testing layers remain incomplete.

## 4) Current Constraints to Fully Realized Core/API

- API drift risk in feature modules when core contracts evolve quickly.
- ACE saturation gap: parser exists, but not all high-value product effects are covered.
- Animation protocol depth: base packet spawning exists; richer metadata/interpolation parity still incomplete.
- Data duality not fully wired: SQL is primary; Mongo/Redis path is scaffolded but not yet injected into runtime startup/config.
- Limited soak/perf harness: no long-duration automated uptime test pack yet.
- Some legacy scheduler compatibility calls remain; migration to strict `runAtLocation/runAtEntity` policy is still in progress.

## 5) 500+ Player Optimization Priorities (Months-Uptime Safety)

- Enforce zero blocking on server thread:
  - DB/HTTP/Redis on async executor only.
  - World/entity mutations only on region/entity schedulers.
- Bound all in-memory registries and caches with TTL/size limits.
- Ensure every repeating task has lifecycle shutdown/cancel path.
- Add meter cleanup for per-player/ephemeral labels on quit/unload.
- Add circuit-breaker defaults for external integrations and control-plane calls.
- Prefer packet/display visuals over persistent server entities for cosmetics/animations.

## 6) Next 20 Steps (Point-Weighted, Dependency Order)

1. **8 pts** Add runtime config block for Mongo/Redis and wire optional `DataStore` bootstrap in `zakum-core`.
2. **8 pts** Register `DataStore` as optional Bukkit service/capability when enabled.
3. **7 pts** Add scheduler guardrails (`main-thread asserts`) around known I/O hot paths.
4. **7 pts** Extend ACE targeters (`@RADIUS`, `@NEARBY`, `@ALLIES`) with bounded entity scans.
5. **7 pts** Complete top 30 production effects in `StandardEffects` with validation.
6. **6 pts** Implement animation metadata writers for display interpolation/item metadata paths.
7. **6 pts** Add benchmark command for ACE execution and packet animation throughput.
8. **6 pts** Add long-lived task registry diagnostics command.
9. **6 pts** Add per-module schema/data health check command.
10. **6 pts** Build cross-module API compatibility smoke tests.
11. **5 pts** Convert remaining lore-based identity checks to strict PDC in feature modules.
12. **5 pts** Add resilience policies (retry/circuit-breaker/bulkhead) to all HTTP bridge calls.
13. **5 pts** Add redis-backed ephemeral cache adapter for party/session/progression bursts.
14. **5 pts** Add packet fan-out radius policy controls for crates/pets visuals.
15. **5 pts** Add structured error taxonomy for ACE parse/execute failures.
16. **4 pts** Add migration command for legacy item metadata -> `zakum:id`.
17. **4 pts** Add module-level load-order validation and startup compatibility checks.
18. **4 pts** Add docs for product plugin integration snippets per module.
19. **4 pts** Add 12h automated soak profile (task/memory/queue telemetry assertions).
20. **3 pts** Add release checklist automation for shaded dependency collision audit.

Progress updates after this audit:
- Step 1: completed with typed `datastore.*` settings wiring into core bootstrap.
- Step 2: completed (DataStore optional service/capability registration is active).
- Step 8: completed via `/zakum tasks status` (fallback + CommandAPI).
- Step 9: completed baseline via `/zakum datahealth status` (fallback + CommandAPI).
- Step 13: completed baseline via shared `BurstCacheService` (Redis + local fallback) and `/zakum burstcache status|enable|disable`.
- Step 17: completed via module startup/load-order validator and `/zakum modules status|validate`.
- Step 19: completed baseline via soak automation profile and `/zakum soak start|stop|status`.
- Step 15: completed via ACE parse/execute error taxonomy and `/zakum ace status|errors|clear`.
- Step 9: completed expanded cross-module SQL probes via `/zakum datahealth modules`.
- Step 12: completed via cloud HTTP resilience parity (shared `http.*` retry/circuit policy + cloud status telemetry).
- GrimAC bridge hardening completed via typed `anticheat.grim.*` controls and `/zakum grim status|enable|disable`.

## 7) Diff Mode (This Refactor Pass Only)

Changed files:

- `zakum-api/src/main/java/net/orbis/zakum/api/concurrent/ZakumScheduler.java`
- `zakum-api/src/main/java/net/orbis/zakum/api/storage/DataStore.java`
- `zakum-core/src/main/java/net/orbis/zakum/core/storage/MongoDataStore.java`
- `gradle/libs.versions.toml`
- `zakum-core/build.gradle.kts`
- `docs/17-UNIVERSAL-PLATFORM-AUDIT.md`

Implemented now from the 20-step list:

- Completed Step 3 foundation piece: scheduler async supplier support (`supplyAsync`).
- Completed Step 1 foundational code: `DataStore` API + Mongo/Redis implementation scaffold.
- Completed dependency/shading portion needed for Step 1 (`mongodb-driver-sync`, `jedis`, relocation rules).
- Verified full project compile via `./gradlew.bat clean build --no-daemon`.
