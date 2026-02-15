# IntelliJ Codex Unified Handoff Bundle

## Branch Policy
- Work is applied on a fork branch without rewriting `main`.
- Lane boundaries are preserved:
- Core/API: `zakum-core/**`, `zakum-api/**`, `build.gradle.kts`
- BattlePass: `zakum-battlepass/**`
- Crates: `zakum-crates/**`
- Pets: `zakum-pets/**`

## Applied Inventory
- Build/release verification task: `releaseShadedCollisionAudit`.
- Core/API: ThreadGuard coverage expansion, ACE target bounds, standard effects expansion, API smoke tests.
- BattlePass: reward type/model/parser/executor updates for command/message/script.
- Crates: `script[]` reward execution path and placeholders.
- Pets: lifecycle scripts and placeholders on summon/dismiss/level-up.
- Docs/assets: this bundle, inventory queue doc, and machine handoff JSON.

## Validation Contract
- `git diff --check`
- Required keyword scans across lane paths.
- If plugin/build resolution is unavailable, report as environment limitation and continue with scan-level verification.
