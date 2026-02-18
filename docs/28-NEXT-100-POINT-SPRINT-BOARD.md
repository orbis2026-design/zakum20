﻿# Next 100-Point Sprint Board (Dependency-First E2E)

Date: 2026-02-15
Source baseline: `docs/27-CORE-PRIMER.md`

**Status Update (2026-02-18):** This sprint board is being executed as part of Phase 1 Foundation Hardening. See `EXECUTION_STATUS.md` and `CURRENT_ROADMAP.md` for current progress tracking.

This board preserves the approved point values while reordering implementation by dependency:
`infrastructure reliability -> platform safety -> plugin capability -> cleanup`.

## Execution Order

1. 20 pts - Data/state hardening under load (infrastructure)
2. 20 pts - 500+ player soak automation (infrastructure verification)
3. 10 pts - Bridge reliability matrix (integration hardening)
4. 15 pts - ACE saturation + safety (cross-module capability hardening)
5. 25 pts - Wave A runtime completion (plugin capability delivery)
6. 10 pts - Deprecated/ghost cleanup (stability + docs consistency)

Total: 100 pts

## Ticket Breakdown and Exit Criteria

### 1) Data/State Hardening Under Load (20 pts)

Scope:
- Redis/Mongo/SQL failure and recovery posture.
- Bounded cache and map lifecycle rules (TTL + max-size + unload cleanup).
- Retry/circuit and timeout behavior consistency.

Implementation targets:
- `zakum-core` datastore/bootstrap/recovery paths.
- `zakum-core` health probe and diagnostics surfaces.
- Affected bridge client runtime code where data dependencies exist.

Exit criteria:
- Controlled failure injection for each store path with verified recovery.
- No unbounded per-player/per-session maps in touched paths.
- `verifyPlatformInfrastructure` and module tests pass.

### 2) 500+ Player Soak Automation (20 pts)

Scope:
- 12h+ soak profiles with assertions on memory/task/queue behavior.
- Leak alarms and queue pressure thresholds.
- Regression-ready automation for repeated execution.

Implementation targets:
- stress harness and ops diagnostics command surfaces.
- task registry and queue telemetry assertions.

Exit criteria:
- At least one repeatable 12h profile spec checked into repo.
- Automated fail conditions for task leak, memory drift, queue saturation.
- Runbook updated with soak invocation and interpretation steps.

### 3) Bridge Reliability Matrix (10 pts)

Scope:
- Graceful degrade behavior across all bridge modules.
- Retry/circuit posture validation and version compatibility checks.

Implementation targets:
- `zakum-bridge-*` modules.
- shared bridge compatibility checks and tests.

Exit criteria:
- Compatibility matrix documented and executable as tests/checks.
- Missing dependency and incompatible version paths degrade without hard crash.

### 4) ACE Saturation + Safety (15 pts)

Scope:
- Expand effect coverage while preserving parser/runtime safety.
- Cap diagnostics label cardinality and maintain bounded buffers.

Implementation targets:
- `zakum-core` ACE parser/effect runtime/diagnostics paths.

Exit criteria:
- New effect set merged with tests.
- Fuzz/invalid-input parser tests added for safety boundary.
- Diagnostics show bounded growth characteristics.

### 5) Wave A Runtime Completion (25 pts)

Scope:
- Convert Wave A modules from baseline scaffolds to production-capable runtime behavior.

Point split:
- 9 pts: `orbis-worlds` lifecycle operations (`create`, `load`, `unload`, `tp`, `delete`) with safeguards.
- 8 pts: `orbis-holograms` entity-backed spawn/despawn diff runtime.
- 6 pts: `orbis-loot` key balances + transactional open flow.
- 2 pts: Wave A command/config parser and runtime parity tests.

Exit criteria:
- Full command/config parity implemented for all three modules.
- Safe main-thread boundaries for world mutation and entity operations.
- Tests cover reload/idempotency and failure edges.

### 6) Deprecated/Ghost Cleanup Pass (10 pts)

Scope:
- Remove stale docs/flags/dead paths and duplicate command surfaces.
- Resolve current known doc conflicts.

Immediate targets:
- `README.md` CLI-build deprecation conflict.
- `DEVELOPMENT-GUIDE.md` stale module status and workflow assumptions.

Exit criteria:
- No conflicting build/development instructions across top-level docs.
- Deprecated or dead paths removed or explicitly marked with migration notes.

## Global Guardrails for All Tickets

- Keep feature modules dependent on `zakum-api` only.
- Keep async-first I/O policy and minimal main-thread execution.
- Enforce bounded queues/maps/caches with explicit lifecycle cleanup.
- Ensure reload idempotency (owned tasks + guaranteed cancellation).
- Protect command and packet hooks from spam/abuse patterns.
- Run: `./gradlew verifyPlatformInfrastructure` and touched module tests before merge.
