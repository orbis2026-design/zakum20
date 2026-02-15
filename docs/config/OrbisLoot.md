# OrbisLoot Configuration

Updated (UTC): 2026-02-15T11:59:52Z

## Keys
- `loot.enabled` (boolean, default: `true`)
- `loot.cleanupIntervalTicks` (int, default: `100`, clamped `20..1200`)
- `loot.openCooldownSeconds` (int, default: `1`, clamped `0..300`)
- `loot.maxRewardsPerOpen` (int, default: `1`, clamped `1..10`)
- `crates.<id>.displayName` (string)
- `crates.<id>.permission` (string, optional)
- `crates.<id>.rewards.<rewardId>.weight` (double, required > 0)
- `crates.<id>.rewards.<rewardId>.actions[]` (non-empty list)

## Commands
- `/orbisloot status`
- `/orbisloot reload`
- `/orbisloot simulate <crate> <rolls>`

## Permissions
- `orbisloot.admin`
- `orbisloot.reload`
- `orbisloot.simulate`
