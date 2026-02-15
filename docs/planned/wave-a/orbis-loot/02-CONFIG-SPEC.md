# Config Specification

Updated (UTC): 2026-02-15T11:38:04Z
Module: orbis-loot

## Root Contract
- Parser validates all crate/reward/key sections before activation.
- Invalid crates are skipped; valid crates still load.
- Numeric constraints clamp to safe ranges.

## Key Schema
- `loot.enabled` (boolean, default: `true`)
- `loot.debug` (boolean, default: `false`)
- `loot.openCooldownSeconds` (int, default: `1`, range: `0..300`)
- `loot.maxRewardsPerOpen` (int, default: `1`, range: `1..10`)
- `loot.transactionTimeoutMillis` (int, default: `2000`, range: `100..10000`)
- `keys.virtualEnabled` (boolean, default: `true`)
- `keys.physicalEnabled` (boolean, default: `true`)
- `keys.item.material` (string, default: `TRIPWIRE_HOOK`)
- `keys.item.modelData` (int, default: `0`, range: `0..999999`)
- `crates.<id>.displayName` (string, required)
- `crates.<id>.permission` (string, optional)
- `crates.<id>.broadcastWin` (boolean, default: `false`)
- `crates.<id>.rewards[]` (required)
- `crates.<id>.rewards[].id` (string, required)
- `crates.<id>.rewards[].weight` (double, required, >0)
- `crates.<id>.rewards[].actions[]` (required, 1+)
- `crates.<id>.rewards[].announce` (string, optional)

## Validation Behavior
- Duplicate reward ids within same crate are rejected.
- Reward pools with zero total weight invalidate crate.
- Unknown materials fallback to safe default with warning.
- Missing action lists skip that reward entry.
- Reload swaps registry atomically to avoid half-applied state.
