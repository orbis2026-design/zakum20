# Planned Modules

Updated (UTC): 2026-02-15T11:37:13Z

## Wave A
- orbis-hud: implementation in progress (v1 runtime/commands active, production hardening ongoing).
- orbis-holograms: planning complete for DecentHolograms-parity core.
- orbis-loot: planning complete for ExcellentCrates-parity core.
- orbis-worlds: planning complete for Multiverse-core parity core.

## Wave A Delivery Order (Process-First Pointing)
1. 30 pts - platform gates (`verifyApiBoundaries`, descriptor checks, module conventions).
2. 25 pts - module runtime safety (config validation, reload idempotency, task lifecycle cleanup).
3. 20 pts - command/permission contract completeness (operator workflow + security).
4. 15 pts - upstream parity-critical user features.
5. 10 pts - optional parity features deferred to Wave B.

## File Contract
Each Wave A module maintains:
- `01-UPSTREAM-PARITY-MATRIX.md`
- `02-CONFIG-SPEC.md`
- `04-COMMANDS-PERMISSIONS.md`
- `06-TEST-PLAN.md`

## Multi-Agent Safety
- Keep updates additive.
- Do not remove existing sections; append/revise with timestamped notes.
- Keep implementation aligned with `zakum-api` boundaries.
- External integrations stay in bridge modules.
