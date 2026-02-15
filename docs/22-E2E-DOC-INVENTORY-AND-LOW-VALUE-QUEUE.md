# E2E Doc Inventory and Low-Value Queue

## Scope Snapshot
- Core/API lane: release audit task, ThreadGuard path coverage, ACE target bounding, standard effect expansion, API smoke checks.
- BattlePass lane: `COMMAND`, `MESSAGE`, `ACE_SCRIPT` reward payloads and execution.
- Crates lane: `script[]` reward support with placeholder substitution.
- Pets lane: lifecycle hooks (`summonScript[]`, `dismissScript[]`, `levelUpScript[]`) with pet placeholders.

## Inventory (E2E-Relevant)
- `build.gradle.kts`: `releaseShadedCollisionAudit` reports + fail-fast checks.
- `zakum-core/**`: async ThreadGuard checks, ACE bounds, effects expansion.
- `zakum-api/**`: compatibility smoke tests for capabilities/accessor surface.
- `zakum-battlepass/**`: reward model/loader/executor expansion + defaults.
- `zakum-crates/**`: reward script parsing/execution + defaults.
- `zakum-pets/**`: lifecycle scripts parsing/runtime hooks + defaults.
- `docs/config/**`: BattlePass/Crates/Pets operator-facing docs.

## Low-Value Queue (Deferred)
- Add richer parser diagnostics for malformed reward script lines.
- Add optional metrics counters for lifecycle-script invocation outcomes.
- Expand docs with end-to-end timing diagrams for cross-module execution paths.
