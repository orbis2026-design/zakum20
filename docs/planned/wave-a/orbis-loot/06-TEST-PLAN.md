# Test Plan

Updated (UTC): 2026-02-15T11:38:04Z
Module: orbis-loot

## Unit Matrix
- Weighted picker fairness and deterministic seed replay behavior.
- Key grant/take/open invariants (no negative balances, no double spend).
- Crate config parsing with invalid reward and action cases.

## Integration Matrix
- Open flow end-to-end: command -> validation -> reward dispatch -> persistence.
- Concurrent open attempts on same player do not duplicate rewards.
- Reload under active opens leaves registry and balances consistent.

## Manual Matrix
- Load 5+ crate definitions and verify commands + permissions on Paper 1.21.11.
- Run `simulate` at high roll counts (100k) and compare observed rarity with expected weight.
- Kill/restart server between key operations to verify persistence durability.
- Validate economy bridge absent/present behavior for optional cost mechanics.
