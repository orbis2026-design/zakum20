# Config Specification

Updated (UTC): 2026-02-15T11:38:29Z
Module: orbis-worlds

## Root Contract
- Config defines desired managed world set.
- Loader validates each entry independently; invalid entries are skipped.
- Runtime operations never block main thread for file I/O heavy work.

## Key Schema
- `worlds.enabled` (boolean, default: `true`)
- `worlds.autoLoad` (boolean, default: `true`)
- `worlds.safeTeleport` (boolean, default: `true`)
- `worlds.teleportWarmupTicks` (int, default: `0`, range: `0..200`)
- `worlds.maxParallelWorldLoads` (int, default: `2`, range: `1..8`)
- `storage.file` (string, default: `worlds.yml`)
- `storage.backupCount` (int, default: `3`, range: `0..20`)
- `managed.<id>.name` (string, required)
- `managed.<id>.environment` (enum: `NORMAL|NETHER|THE_END`, default: `NORMAL`)
- `managed.<id>.seed` (long|string, optional)
- `managed.<id>.generator` (string, optional)
- `managed.<id>.autoLoad` (boolean, default: inherit root)
- `managed.<id>.allowFlight` (boolean, default: `true`)
- `managed.<id>.pvp` (boolean, default: `true`)
- `managed.<id>.difficulty` (enum, default: `NORMAL`)
- `managed.<id>.spawn.x|y|z|yaw|pitch` (double/float, optional)

## Validation Behavior
- Unknown environments/difficulties fallback to defaults with warnings.
- Duplicate world ids are rejected.
- Missing world folder for `import` mode logs warning and entry remains disabled.
- Safe teleport computes fallback using highest non-liquid block near target.
- Reload compares desired state vs runtime and applies minimal diff operations.
