# Test Plan

Updated (UTC): 2026-02-15T11:38:29Z
Module: orbis-worlds

## Unit Matrix
- World config parsing (enums, seeds, spawn structs, duplicates).
- Safe teleport resolver returns valid fallback points.
- Diff planner outputs minimal load/unload operations for config changes.

## Integration Matrix
- Create/import/load/unload/delete command flows against Paper 1.21.11.
- Reload during active players in managed worlds preserves player safety.
- Persistence round-trip for managed world metadata across restarts.

## Manual Matrix
- Validate permission gates for all world-admin commands.
- Perform staged load tests with multiple world transitions while monitoring TPS.
- Validate fallback behavior when referenced world folders are missing/corrupt.
- Verify world deletion guardrail (`confirm`) prevents accidental destruction.
