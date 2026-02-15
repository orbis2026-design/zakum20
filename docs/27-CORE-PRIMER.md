# Core Primer (2026-02-15)

This document is the canonical execution baseline for Orbis platform development.

## Network Identity

- Network name: `Orbis`
- Brand gradient token: `<gradient:#38bdf8:#60a5fa>Orbis</gradient>`
- Tier model: `Cirrus -> Cumulus -> Stratus -> Nimbus -> Zenith`
- Tier gradients (must remain gradient-compatible in all UX output):
  - Cirrus: `#8EDCFF -> #D8F4FF`
  - Cumulus: `#FFB3C7 -> #FFF0D8`
  - Stratus: `#A8FFE3 -> #FFF2B0`
  - Nimbus: `#D3B6FF -> #6FA8FF`
  - Zenith: `#9FB7FF -> #FFEAA6`

## Platform Goal

- Build a network-grade backend platform on Paper/Spigot `1.21.11` + Java `21`.
- Maintain Velocity-aware architecture where proxy concerns stay in Velocity.
- Keep `zakum-core` as shared runtime spine.
- Keep feature plugins dependent on `zakum-api` only.

References:
- `docs/00-OVERVIEW.md`
- `docs/01-MODULES.md`
- `docs/14-CORE-API-FOUNDATION.md`

## Architectural Constraints

- Async-first I/O and minimal main-thread work.
- Bridge-based integrations for external systems.
- Typed config snapshots and SemVer API discipline.
- Long-uptime stability as a first-class requirement.

References:
- `docs/00-OVERVIEW.md`
- `docs/02-THREADING.md`
- `docs/14-CORE-API-FOUNDATION.md`

## Delivery Governance

- Use point-weighted boards and mandatory process gates.
- Prioritize infrastructure/process work before feature expansion.

References:
- `docs/18-CORE-BONES-DIMENSION.md`
- `docs/19-E2E-POINT-REWEIGHT.md`
- `docs/22-ANY-PLUGIN-INFRASTRUCTURE-DIRECTIVE.md`
- `docs/25-DEVELOPMENT-PROCESS-PRIORITY-DIRECTIVE.md`
- `docs/26-E2E-FEATURE-BOARD.md`

## Strategic Policy

- Follow OSS absorption strategy:
  - bridge proven systems,
  - do not re-clone premium/S-tier foundations,
  - invest custom effort in ACE orchestration, progression, UX cohesion, and capability composition.

Reference:
- `docs/24-OSS-ABSORPTION-DIRECTIVE.md`

## Deployment Assumption

- Backend services run on Paper servers.
- Velocity handles proxy concerns separately.
- Zakum may expose optional control-plane integration.

References:
- `docs/00-OVERVIEW.md`
- `docs/03-CONFIG.md`

## Planned Plugin Landscape

Core platform:
- `zakum-core`
- `zakum-api`
- `zakum-packets`
- `orbis-gui`

Feature modules:
- `zakum-battlepass`
- `zakum-crates`
- `zakum-pets`
- `orbis-essentials`
- `orbis-hud`

Wave A expansion:
- `orbis-holograms`
- `orbis-loot`
- `orbis-worlds`

Bridge modules:
- `zakum-bridge-vault`
- `zakum-bridge-placeholderapi`
- `zakum-bridge-luckperms`
- `zakum-bridge-essentialsx`
- `zakum-bridge-jobs`
- `zakum-bridge-mythicmobs`
- `zakum-bridge-citizens`
- `zakum-bridge-votifier`
- `zakum-bridge-superiorskyblock2`
- `zakum-bridge-commandapi`

References:
- `docs/planned/README.md`
- `docs/22-PLUGIN-ECOSYSTEM-CONSOLIDATION.md`
- `docs/05-BRIDGES.md`
- `docs/12-bridges.md`
- `README.md`

## Required Infrastructure

- Mandatory process gates:
  - `verifyApiBoundaries`
  - `verifyPluginDescriptors`
  - `verifyModuleBuildConventions`
  - `verifyPlatformInfrastructure`
- Stable SDK/scaffolding:
  - `zakum-api` `ZakumPluginBase`
  - `tools/new-plugin-module.ps1`
  - `docs/23-PLUGIN-DEVKIT.md`
- Runtime safety baseline:
  - thread guard
  - bounded async/backpressure
  - diagnostics
  - data health probes
  - soak tooling
- Data/protocol spine:
  - SQL + Flyway + Hikari
  - Mongo/Redis paths
  - PacketEvents substrate
  - CommandAPI typed command surface
- Dependency alignment:
  - Paper API pinned to `1.21.11-R0.1-SNAPSHOT` policy
  - bridge isolation

References:
- `docs/22-ANY-PLUGIN-INFRASTRUCTURE-DIRECTIVE.md`
- `docs/23-PLUGIN-DEVKIT.md`
- `docs/18-CORE-BONES-DIMENSION.md`
- `docs/stress-harness-v2.md`
- `docs/26-E2E-FEATURE-BOARD.md`
- `docs/14-CORE-API-FOUNDATION.md`
- `docs/15-REFRACTOR-DIRECTIVE-SPEC.md`

## Priority Board (Next 100 Points, E2E-First)

1. 25 pts: Wave A runtime completion (`orbis-worlds`, `orbis-holograms`, `orbis-loot`) with full command/config/test parity.
2. 20 pts: Data/state hardening under load (Redis/Mongo/SQL failure/recovery and bounded cache lifecycles).
3. 20 pts: 500+ player soak automation (12h+ profiles, memory/task/queue assertions, leak alarms).
4. 15 pts: ACE saturation + safety (effect coverage, parser hardening, diagnostics cardinality bounds).
5. 10 pts: Bridge reliability matrix (graceful degrade, retry/circuit posture, version compatibility tests).
6. 10 pts: Deprecated/ghost cleanup pass (stale code/docs, dead flags, duplicate command paths).

## 500+ Concurrency Optimization Focus

- Enforce strict async boundaries and region-safe world mutation.
- Bound all queues/caches/maps with TTL + max-size + explicit unload cleanup.
- Prevent metric label cardinality explosion and clean per-player labels on quit/disable.
- Make reload idempotent with task ownership registry + guaranteed cancellation.
- Guard command/packet hooks against spam (rate limits, max hooks per plugin).
- Keep feature plugins on `zakum-api` only.

## Documentation Consistency Queue

- `DEVELOPMENT-GUIDE.md` appears stale vs current module reality.
- `README.md` conflicts with process docs by stating CLI builds are deprecated while Gradle verify commands remain canonical.
- Run a docs consistency pass under the deprecated/ghost cleanup item.

## Diff Mode Rule (Refactor Tasks)

- Return changed hunks only, plus changed file list and rationale per hunk.
- Do not include unchanged code dumps.
- Include perf/memory impact note for each refactor.
