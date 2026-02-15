# OrbisWorlds Configuration

Updated (UTC): 2026-02-15T11:59:52Z

## Keys
- `worlds.enabled` (boolean, default: `true`)
- `worlds.updateIntervalTicks` (int, default: `40`, clamped `10..400`)
- `worlds.autoLoad` (boolean, default: `true`)
- `worlds.safeTeleport` (boolean, default: `true`)
- `worlds.maxParallelWorldLoads` (int, default: `2`, clamped `1..8`)
- `managed.<id>.name` (string, default: `<id>`)
- `managed.<id>.autoLoad` (boolean, default: inherits `worlds.autoLoad`)

## Commands
- `/orbisworld status`
- `/orbisworld reload`

## Permissions
- `orbisworlds.admin`
- `orbisworlds.reload`
