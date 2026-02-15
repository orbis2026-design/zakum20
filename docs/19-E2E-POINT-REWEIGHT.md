# E2E Point Reweight (Cross-Board)

This board recalculates priorities by end-to-end leverage on all future plugin and code development.

## Scoring Formula

Each initiative gets a weighted score:

- `E2E leverage` (40%): impact across all modules/services.
- `stability gain` (25%): uptime and failure-surface reduction.
- `development velocity gain` (20%): faster feature delivery and lower regression risk.
- `time intensity` (15%): engineering time and integration complexity.

Scores are normalized to a 100-point rank, then mapped to queue priority.

## Current Cross-Board Ranking

1. **96/100** API contract + boundary enforcement (`zakum-api` smoke tests + `verifyApiBoundaries`) - complete
2. **94/100** Scheduler runtime safety (async backpressure + thread guard controls) - complete
3. **92/100** Command/control diagnostics spine (`/zakum datahealth|tasks|async|threadguard`) - complete
4. **90/100** Typed storage bootstrap (`datastore.*` typed settings + runtime wiring) - complete
5. **88/100** HTTP resilience parity for all bridges/control-plane clients - complete (ControlPlane path)
6. **84/100** Redis burst cache adapter for shared transient workloads - complete
7. **82/100** Module startup/load-order validator - complete
8. **80/100** 12h soak automation profile with assertions - complete
9. **78/100** Structured ACE parse/execute error taxonomy - pending

## Execution Rule (Directive Alignment)

- Select the next ticket by highest E2E rank, not plugin-local value.
- Require a vertical slice per ticket: config + runtime + command/control + metrics + failure handling + docs.
- Keep all new feature modules constrained to `zakum-api` contracts (no `zakum-core` imports).

## Next Priority Slice

`Structured ACE parse/execute error taxonomy` is now the highest-scoring pending initiative.
